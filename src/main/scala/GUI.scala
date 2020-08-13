package me.winslow.d.mn2gt
import driver._

import scala.swing._
import javax.swing

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GUI extends SwingApplication {

  import GridBagPanel._

  sealed trait OperationMode
  object Local extends OperationMode
  object Remote extends OperationMode

  object State {
    var mapnikFile: Option[java.io.File] = None
    var outputDir: Option[java.io.File] = None
    var mode: OperationMode = Local
    var geoserverConnection: Option[GeoServerConnection] = None

    def isValid =
      mode match {
        case Local =>
          mapnikFile.isDefined && outputDir.isDefined
        case Remote =>
          mapnikFile.isDefined && geoserverConnection.isDefined
      }

    def job: Option[Operation] =
      mode match {
        case Local =>
          for {
            mf <- mapnikFile
            od <- outputDir
          } yield LocalConversion(mf, od)
        case Remote =>
          for {
            mf <- mapnikFile
            conn <- geoserverConnection
          } yield PublishToGeoServer(mf, conn)
      }
  }

  case class ConnectionSpecified(conn: GeoServerConnection)
    extends scala.swing.event.Event

  class InputBox extends GridBagPanel {
    val mapnikFileLabel = new Label("Mapnik XML")
    val mapnikFileField = new TextField(30)
    val fileChooserLauncher = new Button("Open...")
    private val fileChooser = new FileChooser()
    fileChooser.fileSelectionMode =
      FileChooser.SelectionMode.FilesOnly

    fileChooserLauncher.action = Action("Open...") {
      if (fileChooser.showOpenDialog(this) == FileChooser.Result.Approve)
        mapnikFileField.text = fileChooser.selectedFile.getAbsolutePath
    }
    

    border = new swing.border.TitledBorder("Input")
    enabled = false
    layout ++= Seq(
      mapnikFileLabel -> ((0, 0): Constraints),
      mapnikFileField -> ((1, 0): Constraints),
      fileChooserLauncher -> ((2, 0): Constraints)
    )
  }

  class OperationBox extends GridBagPanel {
    val local = new RadioButton("Just save SLD files to local disk")
    val publish = new RadioButton("Really publish layers to GeoServer")
    val group = new ButtonGroup(local, publish)

    border = new swing.border.TitledBorder("Operation")
    layout ++= Seq(
      local -> new Constraints {
        grid = (0, 0)
        anchor = Anchor.West
      },
      publish -> new Constraints {
        grid = (0, 1)
        anchor = Anchor.West
      }
    )
  }

  class OutputBox extends GridBagPanel {
    val outputDirLabel = new Label("SLD directory")
    val outputDirField = new TextField(30)
    val fileChooserLauncher = new Button("Open...")
    private val fileChooser = new FileChooser()
    fileChooser.fileSelectionMode =
      FileChooser.SelectionMode.DirectoriesOnly

    fileChooserLauncher.action = Action("Open...") {
      if (fileChooser.showOpenDialog(this) == FileChooser.Result.Approve)
        outputDirField.text = fileChooser.selectedFile.getAbsolutePath
    }

    border = new swing.border.TitledBorder("Local Output")

    override def enabled_=(b: Boolean) = {
      super.enabled_=(b)
      outputDirLabel.enabled = b
      outputDirField.enabled = b
      fileChooserLauncher.enabled = b
    }

    layout ++= Seq(
      outputDirLabel -> ((0, 0): Constraints),
      outputDirField -> ((1, 0): Constraints),
      fileChooserLauncher -> ((2, 0): Constraints)
    )
  }

  class GeoServerBox extends GridBagPanel {
    val urlLabel = new Label("Server URL")
    val urlField =
      new TextField("http://localhost:8080/geoserver/rest", 30)
    val adminLabel = new Label("Admin user")
    val adminField = new TextField("admin", 30)
    val passwordLabel = new Label("Password")
    val passwordField = new PasswordField(30)
    val nsPrefixLabel = new Label("Namespace Prefix")
    val nsPrefixField = new TextField("mn2gt", 30)
    val nsUriLabel = new Label("Namespace URI")
    val nsUriField = new TextField("http://example.com/mn2gt/", 30)

    private val fields = 
      Seq(
        urlField,
        adminField,
        passwordField,
        nsPrefixField,
        nsUriField,
        urlLabel,
        adminLabel,
        passwordLabel,
        nsPrefixLabel,
        nsUriLabel
      )

    listenTo(
      urlField,
      adminField,
      passwordField,
      nsPrefixField,
      nsUriField
    )

    reactions += {
      case event.EditDone(_) =>
        for {
          url <- Some(urlField.text) filter(_ nonEmpty)
          user <- Some(adminField text) filter(_ nonEmpty)
          password <- Some(passwordField password) filter(_ nonEmpty)
          nsPrefix <- Some(nsPrefixField text) filter(_ nonEmpty)
          nsUri <- Some(nsUriField text) filter(_ nonEmpty)
        } publish(ConnectionSpecified(GeoServerConnection(
            url, user, password.mkString, nsPrefix, nsUri
          )))
      case _ => ()
    }

    override def enabled_=(b: Boolean) {
      super.enabled_=(b)
      fields.foreach(_.enabled = b)
    }

    border = new swing.border.TitledBorder("GeoServer")
    layout ++= Seq(
      urlLabel -> ((0, 0): Constraints),
      urlField -> ((1, 0): Constraints),
      adminLabel -> ((0, 1): Constraints),
      adminField -> ((1, 1): Constraints),
      passwordLabel -> ((0, 2): Constraints),
      passwordField -> ((1, 2): Constraints),
      nsPrefixLabel -> ((0, 3): Constraints),
      nsPrefixField -> ((1, 3): Constraints),
      nsUriLabel -> ((0, 4): Constraints),
      nsUriField -> ((1, 4): Constraints)
    )
  }

  class CommitBox extends GridBagPanel {
    val button = new Button("Convert!")

    override def enabled_=(b: Boolean) = {
      super.enabled_=(b)
      button.enabled = b
    }

    layout ++= Seq(
      new Component {}  -> new Constraints {
        grid = (0, 0)
        weightx = 1
        fill = Fill.Both
      },
      button -> new Constraints {
        grid = (1, 0)
        anchor = Anchor.LastLineEnd
      }
    )
  }

  class ProgressReporter(owner: Window) extends Dialog(owner) {
    modal = true
    title = "Converting..."
    
    val progress = new ProgressBar
    progress.label = "Converting Mapnik style to GeoServer SLD..."
    progress.indeterminate = true

    val closeButton = Button("Okay")({ visible = false})
    closeButton.enabled = false

    def finish() {
      progress.indeterminate = false
      progress.max = 1
      progress.value = 1
      closeButton.enabled = true
    }

    contents = new GridBagPanel { 
      layout += progress -> (0, 0)
      layout += (closeButton -> (1, 0))
    }
  }
  
  def startup(args: Array[String]) {
    locally {
      import javax.swing.UIManager.{ getInstalledLookAndFeels, setLookAndFeel }
      import scala.jdk.CollectionConverters._
      for (nimbus <- getInstalledLookAndFeels.find(_.getName == "Nimbus"))
        setLookAndFeel(nimbus.getClassName)
    }

    val frame = new MainFrame
    val input = new InputBox
    val operation = new OperationBox
    val output = new OutputBox
    val geoserver = new GeoServerBox
    val commit = new CommitBox 

    def enableAppropriateControls = {
      commit.enabled = State.isValid
      output.enabled = (State.mode == Local)
      geoserver.enabled = (State.mode == Remote)
    }

    locally {
      import scala.swing.event._

      input.mapnikFileField.reactions += {
        case ValueChanged(c: TextField) =>
          State.mapnikFile = Some(new java.io.File(c.text))
          enableAppropriateControls
        case _ => 
      }

      operation.local.reactions += {
        case ButtonClicked(_) => 
          State.mode = Local
          enableAppropriateControls
        case _ => ()
      }
      
      output.outputDirField.reactions += {
        case ValueChanged(c: TextField) =>
          State.outputDir = Some(new java.io.File(c.text))
          enableAppropriateControls
        case _ => ()
      }

      operation.publish.reactions += {
        case ButtonClicked(_) =>
          State.mode = Remote
          enableAppropriateControls
        case _ => ()
      }

      geoserver.reactions += {
        case ConnectionSpecified(conn) =>
          State.geoserverConnection = Some(conn)
          enableAppropriateControls
        case _ => ()
      }

      commit.button.reactions += {
        case ButtonClicked(_) =>
          val reporter = new ProgressReporter(frame)
          reporter.setLocationRelativeTo(frame)
          State.job foreach { job =>
            Future {
              try
                job.run()
              catch { case ex: Throwable =>
                reporter.visible = false
                val buff = new java.io.StringWriter
                val writer = new java.io.PrintWriter(buff)
                ex.printStackTrace(writer)
                writer.flush()
                Dialog.showMessage(
                  message = buff.toString,
                  messageType = Dialog.Message.Error
                )
              }

              reporter.finish()
            }
          }
          reporter.visible = true
      }
    }

    operation.local.selected = true
    enableAppropriateControls

    locally { import frame._
      title = "Mapnik → GeoServer Importer"
      visible = true
      contents = {
        val grid = new BoxPanel(Orientation.Vertical)
        grid.contents ++= Seq(
          input,
          operation,
          output,
          geoserver,
          commit
        )

        grid
      }
    }
  }
}
