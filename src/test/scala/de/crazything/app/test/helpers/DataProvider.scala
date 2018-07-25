package de.crazything.app.test.helpers

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import de.crazything.app.Person

import scala.collection.mutable.ListBuffer

object DataProvider {

  def readPersons(): Seq[Person] = {
    import scala.collection.JavaConverters._
    val url: URL = DataProvider.getClass.getResource("/persons.txt")
    val path: Path = Paths.get(url.toURI)
    val lines: Seq[String] = Files.readAllLines(path).asScala
    val buffer: ListBuffer[Person] = new ListBuffer
    lines.foreach(line => {
      val arr: Array[String] = line.split(";")
      buffer.append(Person(arr(0).toInt, arr(1), arr(2), arr(3), arr(4), arr(5)))
    })
    buffer
  }

}