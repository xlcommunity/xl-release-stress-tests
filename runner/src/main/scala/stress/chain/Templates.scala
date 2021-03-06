package stress.chain

import io.gatling.core.Predef._
import io.gatling.http.Predef._


object Templates {

  def open = exec(
    http("All templates")
      .get("/releases/templates?filter=")
      .queryParam("numberbypage", "15")
      .queryParam("page", "0")
      .asJSON
  )

  def findTemplatesByTitle(title: String) = exec(
    http("Find template by title")
      .get(s"/releases/templates?filter=$title")
      .queryParam("numberbypage", "15")
      .queryParam("page", "0")
      .asJSON
      .check(
        jsonPath("$['cis'][*]['id']")
          .findAll
          .saveAs("foundTemplateIds")
      )
  )
}
