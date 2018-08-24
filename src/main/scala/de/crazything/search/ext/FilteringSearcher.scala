package de.crazything.search.ext

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors}

import de.crazything.search.entity.{PkDataSet, QueryCriteria, SearchResult}
import de.crazything.search.ext.RunnableHandlers.FilterFutureHandler
import de.crazything.search.utils.FutureUtil
import de.crazything.search.{AbstractTypeFactory, CommonSearcher, DirectoryContainer, MagicSettings}
import org.apache.lucene.search.IndexSearcher
import play.api.libs.json.OFormat

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

/**
  * Combine searches with other filters, maybe other searches.
  */
object FilteringSearcher extends SimpleFiltering with MagicSettings {

  // I bet, this will be changed soon.
  // TODO: Don't forget this line! The ThreadPool should not be sufficient, if it comes to real mass processing.
  import scala.concurrent.ExecutionContext.Implicits.global

  private def processSecondLevel[I1, T1 <: PkDataSet[I1]]
  (primaryResult: Seq[SearchResult[I1, T1]],
   filterClass: (Seq[SearchResult[I1, T1]]) => Filter[I1, T1],
   filterTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT,
   promise: Promise[Seq[SearchResult[I1, T1]]]): Unit = {

    val filteringClass: Filter[I1, T1] = filterClass(primaryResult)
    val finalResultFuture = FutureUtil.futureWithTimeout(filteringClass.createFuture(), filterTimeout)

    finalResultFuture.onComplete {
      case Success(finalResult) =>
        if (finalResult.nonEmpty) {
          promise.success(finalResult.sortBy(res => -res.score))
        } else {
          promise.success(Seq())
        }
      case Failure(t: TimeoutException) =>
        filteringClass.onTimeoutException(t)
        promise.failure(t)
      case Failure(x) => promise.failure(x)
    }

  }

  private def processFirstLevel[I, T <: PkDataSet[I]]
  (primaryResultFuture: Future[Seq[SearchResult[I, T]]],
   getFilterClass: (Seq[SearchResult[I, T]]) => Filter[I, T],
   filterTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT): Future[Seq[SearchResult[I, T]]] = {
    val promise: Promise[Seq[SearchResult[I, T]]] = Promise[Seq[SearchResult[I, T]]]
    primaryResultFuture.onComplete {
      case Success(res) =>
        processSecondLevel(res, getFilterClass, filterTimeout, promise)
      case Failure(t) => promise.failure(t)
    }
    promise.future
  }

  def search[I, T <: PkDataSet[I]]
  (input: T,
   factory: AbstractTypeFactory[I, T],
   searcherOption: Option[IndexSearcher] = DirectoryContainer.defaultSearcher,
   queryCriteria: Option[QueryCriteria] = None,
   maxHits: Int = MAGIC_NUM_DEFAULT_HITS_FILTERED,
   filterFn: (SearchResult[I, T]) => Future[Boolean],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT): Future[Seq[SearchResult[I, T]]] = {
    def secondLevelClass(res: Seq[SearchResult[I, T]]): Filter[I, T] = new FilterAsyncFuture(res, filterFn)
    val searchResult: Future[Seq[SearchResult[I, T]]] = CommonSearcher.searchAsync(input, factory, queryCriteria, maxHits, searcherOption)
    processFirstLevel(searchResult, secondLevelClass, secondLevelTimeout)
  }

  def searchFuture[I, T <: PkDataSet[I]]
  (initialFuture: Future[Seq[SearchResult[I, T]]],
   filterFn: (SearchResult[I, T]) => Future[Boolean],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT)
  (implicit fmt: OFormat[T]): Future[Seq[SearchResult[I, T]]] = {
    def secondLevelClass(res: Seq[SearchResult[I, T]]): Filter[I, T] = new FilterAsyncFuture(res, filterFn)
    processFirstLevel(initialFuture, secondLevelClass, secondLevelTimeout)
  }

  def searchRemote[I, T <: PkDataSet[I]]
  (input: T,
   url: String,
   firstLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT,
   filterFn: (SearchResult[I, T]) => Future[Boolean],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT)
  (implicit fmt: OFormat[T]): Future[Seq[SearchResult[I, T]]] = {
    def secondLevelClass(res: Seq[SearchResult[I, T]]): Filter[I, T] = new FilterAsyncFuture(res, filterFn)
    val searchResult: Future[Seq[SearchResult[I, T]]] = CommonSearcher.searchRemote[I, T](input, url, firstLevelTimeout)
    processFirstLevel(searchResult, secondLevelClass, secondLevelTimeout)
  }

  // Do not make this private. We have to mock it in some tests.
  val processors: Int = Runtime.getRuntime.availableProcessors()

  trait Filter[I, T <: PkDataSet[I]] {
    // Yes, we can do this here. We take care of the pool in our createFuture methods.
    val pool: ExecutorService = Executors.newFixedThreadPool(processors)
    val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
    val promise: Promise[Seq[SearchResult[I, T]]] = Promise[Seq[SearchResult[I, T]]]
    val buffer: ListBuffer[SearchResult[I, T]] = ListBuffer[SearchResult[I, T]]()
    val counter = new AtomicInteger()
    val procCount = new AtomicInteger()

    def onTimeoutException(exc: Exception): Unit = {
      pool.shutdownNow()
    }

    def onFilterException(exc: Throwable): Unit = {
      if (!promise.isCompleted) {
        promise.failure(exc)
        pool.shutdownNow()
      }
    }

    def createFuture(): Future[Seq[SearchResult[I, T]]]

    protected def doCreateFuture(raw: Seq[SearchResult[I, T]], body: (Int) => Unit): Future[Seq[SearchResult[I, T]]] = {
      body(raw.length)
      promise.future
    }
  }

  private class FilterAsyncFuture[I, T <: PkDataSet[I]](raw: Seq[SearchResult[I, T]],
                                                        filterFn: (SearchResult[I, T]) => Future[Boolean])
    extends Filter[I, T] {
    override def createFuture(): Future[Seq[SearchResult[I, T]]] = {
      doCreateFuture(raw, (len: Int) => {
        def checkLenInc(): Unit = if (counter.incrementAndGet() == len) {
          promise.success(buffer)
          pool.shutdown()
        } else if (procCount.get() < len) {
          pool.execute(new FilterFutureHandler(filterFn, raw(procCount.getAndIncrement()), buffer, () => checkLenInc(), onFilterException)(ec))
        }

        val shorter = if (processors < len) processors else len
        for (i <- 0 until shorter) {
          procCount.incrementAndGet()
          pool.execute(new FilterFutureHandler(filterFn, raw(i), buffer, () => checkLenInc(), onFilterException)(ec))
        }
      })
    }
  }

}
