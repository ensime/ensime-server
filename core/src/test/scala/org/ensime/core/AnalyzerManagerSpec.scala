package org.ensime.core

import akka.actor.{ Actor, Props }
import akka.testkit.TestActorRef
import org.ensime.api.{ EnsimeConfig, EnsimeProject, EnsimeProjectId, EnsimeServerError, TypecheckFilesReq, VoidResponse }
import org.ensime.fixture.SharedTestKitFixture
import org.ensime.util.EnsimeSpec
import org.ensime.util.FileUtils.toSourceFileInfo
import org.ensime.util.ensimefile.EnsimeFile
import org.ensime.util.file.{ RichFile, withTempDir }

class AnalyzerManagerSpec extends EnsimeSpec with SharedTestKitFixture {

  "Analyzer Manager" should "aggregate multiple EnsimeServerErrors into one" in withTestKit { testKist =>
    import testKist._

    class DummyFileMissingAnalyzer extends Actor {
      override def receive: Receive = {
        case TypecheckFilesReq(files) =>
          val missingFilePaths = files.map { f => "\"" + toSourceFileInfo(f).file + "\"" }.mkString(",")
          sender ! EnsimeServerError(s"file(s): ${missingFilePaths} do not exist")
      }
    }
    withTempDir(dir => {
      val module1 = dir / "module1"
      val module2 = dir / "module2"
      if (module1.mkdir() && module2.mkdir()) {
        implicit val dummyConfig: EnsimeConfig = EnsimeConfig(dir, dir, dir, null, null, Nil, Nil, List(
          EnsimeProject(EnsimeProjectId("module1", "compile"), Nil, Set(module1), Set.empty, Nil, Nil, Nil, Nil, Nil),
          EnsimeProject(EnsimeProjectId("module2", "compile"), Nil, Set(module2), Set.empty, Nil, Nil, Nil, Nil, Nil)
        ), Nil)
        val analyzerManager = TestActorRef(AnalyzerManager(TestActorRef[Broadcaster], (module) => Props(new DummyFileMissingAnalyzer())))
        val file1 = module1 / "missing1.scala"
        val file1Path = file1.getAbsolutePath
        val file2 = module2 / "missing2.scala"
        val file2Path = file2.getAbsolutePath

        analyzerManager ! TypecheckFilesReq(List(Left(file1), Left(file2)))
        val error = expectMsgType[EnsimeServerError].description
        error should include(s"""file(s): "${EnsimeFile(file1Path)}" do not exist""")
        error should include(s"""file(s): "${EnsimeFile(file2Path)}" do not exist""")
      }
    })

  }

  it should "ask to update .ensime if module for a file is not found" in withTestKit { testKit =>
    import testKit._

    class DummyAnalyzer extends Actor {
      override def receive: Receive = {
        case TypecheckFilesReq(files) =>
          sender ! VoidResponse
      }
    }

    withTempDir(dir => {

      implicit val dummyConfig: EnsimeConfig = EnsimeConfig(dir, dir, dir, null, null, Nil, Nil, Nil, Nil)
      val analyzerManager = TestActorRef(AnalyzerManager(TestActorRef[Broadcaster], (module) => Props(new DummyAnalyzer)))
      val testFile = dir / "inNoModule.scala"

      analyzerManager ! TypecheckFilesReq(List(Left(testFile)))
      expectMsg(EnsimeServerError("Update .ensime file."))

    })
  }

}