package me.winslow.d.mn2gt

import org.specs2._

import scala.xml._
import Mapnik2GeoTools._

import scala.xml.NodeSeq.seqToNodeSeq

object Mapnik2GeoToolsSpec extends mutable.Specification {

  implicit class NodeOps(val node: Node) extends AnyVal {
    def mustXML_==(node2: Node) =
      Utility.trimProper(node) must_== Utility.trimProper(node2)
  }

  "osm.xml should work" in {
    val tx = new transform.RuleTransformer(LineSymTransformer, RuleCleanup)
    "support patterns" in {
      val transformed =
        tx(<LinePatternSymbolizer file="symbols/chair_lift.png"></LinePatternSymbolizer>)

      transformed.label must_== "LineSymbolizer"
    }.pendingUntilFixed

    "expand stroke colors" in {
      "short hex" >> {
	      val shorthex =
	        tx(
	          <LineSymbolizer>
                <CssParameter name="stroke">#888</CssParameter>
	          </LineSymbolizer>
	        )

        (shorthex \\ "CssParameter").text must_== "#888888"
      }

      "named color" >> {
	      val namedcolor =
	        tx(
	          <LineSymbolizer>
                <CssParameter name="stroke">salmon</CssParameter>
              </LineSymbolizer>
	        )

        (namedcolor \\ "CssParameter").text must_== "#fa8072"
      }
      
      "rgb" >> {
	      val rgb =
	        tx(
	          <LineSymbolizer>
                <CssParameter name="stroke">rgb(10,0,255)</CssParameter>
              </LineSymbolizer>
	        )

        (rgb \\ "CssParameter").text must_== "#0a00ff"
      }
    }

    "translate dasharrays" in {
      val transformed =
        tx(
          <LineSymbolizer>
            <CssParameter name="stroke-dasharray">2,2</CssParameter>
          </LineSymbolizer>
        )

      (transformed \\ "CssParameter").text must_== "2 2"
    }

    "extract CssParameters from attributes" in {
      val transformed =
        tx(<LineSymbolizer stroke="#b4b4b4" stroke-width="0.5"/>)

      transformed mustXML_== (
        <LineSymbolizer>
          <Stroke>
            <CssParameter name="stroke">#b4b4b4</CssParameter>
            <CssParameter name="stroke-width">0.5</CssParameter>
          </Stroke>
        </LineSymbolizer>
      )
    }
  }
  
  {
    val tx = new transform.RuleTransformer(PointSymTransformer)
    "not require a 'type' attribute" in {
      tx(<PointSymbolizer file="symbols/lock_gate.png" />) mustXML_== (
        <PointSymbolizer>
          <Graphic>
            <ExternalGraphic>
              <OnlineResource xlink:type="simple" xlink:href="symbols/lock_gate.png"/>
              <Format>image/png</Format>
            </ExternalGraphic>
          </Graphic>
        </PointSymbolizer>)
    }
  }
  

  {
    val tx = new transform.RuleTransformer(PolygonSymTransformer)
    "not require a 'height' attribute" in {
      val transformed =
        tx(<PolygonPatternSymbolizer file="symbols/glacier.png" />)
      transformed mustXML_== (
        <PolygonSymbolizer>
          <Fill>
            <GraphicFill>
              <Graphic>
                <ExternalGraphic>
                  <OnlineResource xlink:href="symbols/glacier.png"/>
                  <Format>image/png</Format>
                </ExternalGraphic>
              </Graphic>
            </GraphicFill>
          </Fill>
        </PolygonSymbolizer>
        )
    }

    "extract CssParameters from attributes" in {
      val transformed =
        tx(<PolygonSymbolizer fill-opacity=".25" fill="#999999"/>)

      transformed mustXML_==(
        <PolygonSymbolizer>
          <Fill>
            <CssParameter name="fill-opacity">.25</CssParameter>
            <CssParameter name="fill">#999999</CssParameter>
          </Fill>
        </PolygonSymbolizer>
      )
    }
  }

  {
    val tx =
      new transform.RuleTransformer(
        new me.winslow.d.mn2gt.mapnik2.TextSymbolizerTransformer(Nil)
      )

    "not require a 'type' attribute for shield images" in {
      val transformed =
        tx(
          <ShieldSymbolizer
            name="ref"
            fontset-name="bold-fonts"
            size="10"
            fill="#fff"
            placement="line"
            file="&symbols;/mot_shield1.png"
            min_distance="30"
            spacing="750"
         />)

      (transformed \ "Graphic").head mustXML_== (
          <Graphic>
            <ExternalGraphic>
              <OnlineResource xlink:href="&amp;symbols;/mot_shield1.png"/>
              <Format>image/png</Format>
            </ExternalGraphic>
          </Graphic>
        )
    }

    "keep the <Size> element outside the ExternalGraphic" in {
      (tx(<ShieldSymbolizer file="foo.png" fontset-name="bold-fonts" height="12" width="12"/>) \\ "Graphic" \ "Size").text must_== "12"
    }

    "create halos from only a halo-radius property" in {
      val transformed = 
        tx(<TextSymbolizer halo-radius="10" name="name" fontset-name="bold-fonts"/>)

      (transformed \\ ("Halo")).head mustXML_== (
      <Halo>
        <Radius>
          <ogc:Literal>10</ogc:Literal>
        </Radius>
        <Fill>
          <CssParameter name="fill">#ffffff</CssParameter>
        </Fill>
      </Halo>)
    }

    "support halo-fill in different formats" in {
      "hex" >> {
        val transformed =
          tx(<TextSymbolizer halo-fill="#fed7a5" name="name" fontset-name="book-fonts" size="8" fill="black" halo-radius="1" placement="line"/>)

        (transformed \\ ("Halo") \\ "Fill").head mustXML_== (
          <Fill>
            <CssParameter name="fill">#fed7a5</CssParameter>
          </Fill>
        )
      }

      "rgba" >> {
        val transformed =
          tx(<TextSymbolizer halo-fill="rgba(10,0,255,0.25)" name="name" fontset-name="bold-fonts" size="12" fill="#2b2b2b" halo-radius="2" dy="0" placement="line" max_char_angle_delta="40" text_convert="toupper"/>)

        (transformed \\ ("Halo") \\ "Fill").head mustXML_== (
          <Fill>
            <CssParameter name="fill">#0a00ff</CssParameter>
            <CssParameter name="fill-opacity">0.25</CssParameter>
          </Fill>
        )
      }
    }

    "support fill in different formats" in {
      val transformed =
        tx(<TextSymbolizer dy="-8" fill="rgb(102,102,255)" fontset-name="book-fonts" halo-radius="1" size="8" vertical-alignment="top"/>)

      println(transformed.mkString)
      (transformed \ "Fill").head mustXML_== (
        <Fill>
          <CssParameter name="fill">#6666ff</CssParameter>
        </Fill>
      )
    }

    "new name format" in {
      val transformed =
        tx(<TextSymbolizer fill="rgb(0,0,0)" fontset-name="book-fonts" halo-radius="1" size="8">[name]</TextSymbolizer>)

      (transformed \\ ("Label")).head mustXML_== (
        <Label>
          <ogc:PropertyName>name</ogc:PropertyName>
        </Label>
      )
    }
  }
}
