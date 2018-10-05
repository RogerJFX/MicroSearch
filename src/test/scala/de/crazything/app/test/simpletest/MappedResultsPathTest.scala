package de.crazything.app.test.simpletest

import de.crazything.app.entity.{Person, SkilledPerson, SocialPerson}
import de.crazything.search.entity.{MappedQuery, MappedResults, SearchResult}
import org.scalatest.FlatSpec

import scala.collection.mutable.ListBuffer

class MappedResultsPathTest extends FlatSpec {

  val testResult = Seq(
    MappedResults(
      SearchResult[Int, SkilledPerson](SkilledPerson(21, Some("Roger"), Some("Hösl"),
        Some(ListBuffer("Scala", "Java", "Lucene", "PostgreSQL", "Javascript", "Typescript"))), 27.38844F),
      List(
        SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]](
          MappedResults(
            SearchResult[Int, Person](Person(2, "Herr", "Roger", "Hösl", "Hartmannweg 25", "60389 Frankfurt a.M."), 516.7601F),
            List(
              SearchResult[Int, SocialPerson](SocialPerson(2, "Roger", "Hösl", Some("roger"), None), 84.43614F),
              SearchResult[Int, SocialPerson](SocialPerson(14, "Roger", "Hoesl", Some("roger"), Some("hoesl")), 83.96084F)
            )
          ), 516.7601F),
        SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]](
          MappedResults(
            SearchResult[Int, Person](Person(666, "Mr", "Donald", "Trump", "Highway to hell", "New York"), 1.7601F),
            List(
              SearchResult[Int, SocialPerson](SocialPerson(666, "Donald", "Trump", Some("idiot"), None), 84.43614F)
            )
          ), 1.7601F)
      )
    )
  )

  private val root: MappedResults[Int, Int, SkilledPerson, MappedResults[Int, Int, Person, SocialPerson]] = testResult.head

  "Path" should "find items in a MappedResults mess" in {
    val found: Seq[SearchResult[Int, SocialPerson]] = root.findMappedResults4TargetD(classOf[Person]).get// .asInstanceOf[Seq[SearchResult[Int, SocialPerson]]]
    //val found: Seq[SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]]] = root.findMappedResults4Target(classOf[Person]).get
    println("---------------------------")
    println(found)
    val foo: SearchResult[Int, SocialPerson] = found.head//.origin()
    println(foo)
    println(foo.found.facebookId)
//    assert(foo.found.facebookId.nonEmpty)
    println("######################")
    val found2: Seq[SearchResult[Int, Person]] = root.findMappedResults4TargetD(classOf[SkilledPerson]).get
    println("---------------------------")
    println(found2)
    val bar: SearchResult[Int, Person] = found2.head//.origin()
    assert(bar.found.firstName == "Roger")
    println(found2.head)
    println("-")
    println(bar)

//    val found3:Seq[SearchResult[Int, Person]] = root.findMappedResults4Target(classOf[SkilledPerson]).get
  }

  "MappedQuery" should "find items or not" in {
    val found: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Roger"))))
    println(found)
    assert(found.isDefined)

    val wrongFirstName: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Karl-Heinz"))))
    println(wrongFirstName)
    assert(wrongFirstName.isEmpty)

    val reasonableMinScore: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Roger")), minScore = Some(12F)))
    assert(reasonableMinScore.isDefined)

    val fantasticMinScore: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", minScore = Some(12345F)))
    assert(fantasticMinScore.isEmpty)

    val reasonableMaxScore: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Roger")), maxScore = Some(12345F)))
    assert(reasonableMaxScore.isDefined)

    val fantasticMaxScore: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", maxScore = Some(0.12345F)))
    assert(fantasticMaxScore.isEmpty)
  }

  it should "filter results" in {
    val found: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Roger"))),
        Some(MappedQuery(clazzName="SocialPerson", attributeRxs=Seq(("firstName", "Roger"), ("facebookId", "Some\\(roger\\)")))))
    println(found)
    assert(found.isDefined)
    assert(found.get.nonEmpty)

    val facebookNotFound: Option[Seq[SearchResult[Int, SocialPerson]]] =
      root.findMappedResults4Target(MappedQuery(clazzName="Person", attributeRxs=Seq(("firstName", "Roger"))),
        Some(MappedQuery(clazzName="SocialPerson", attributeRxs=Seq(("firstName", "Roger"), ("facebookId", "not reasonable")))))
    println(facebookNotFound)
    assert(facebookNotFound.isDefined)
    assert(facebookNotFound.get.isEmpty) // We found the Person, but no children of type SocialPerson.
  }

}
