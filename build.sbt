lazy val root = (project in file(".")).
  settings(
    name := "ps-anim-viewer",
    version := "0.1",
    scalaVersion := "2.11.6",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-xml" % "2.11.0-M4",
	  "org.scalafx" %% "scalafx" % "2.2.76-R11",
	  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
	  "com.beachape.filemanagement" %% "schwatcher" % "0.1.7",
	  "com.twelvemonkeys.common" % "common-lang" % "3.0.2",
	  "com.twelvemonkeys.common" % "common-io" % "3.0.2"
      //"com.twelvemonkeys.imageio" % "imageio-core" % "3.1.0",
      //"com.twelvemonkeys.imageio" % "imageio-metadata" % "3.1.0",
      //"com.twelvemonkeys.imageio" % "imageio-psd" % "3.1.0"
    ),
	fork := true,
	
	// Discover and load the JavaFX jar.
	javaHome := Option(System.getenv("JAVA_HOME")).map(new File(_)),
	unmanagedJars in Compile <+= javaHome map { jh =>
      val dir = jh getOrElse { throw new RuntimeException("JAVA_HOME not specified") }
      
      val jfxJar = new File(dir, "/jre/lib/jfxrt.jar")
      if (!jfxJar.exists)
        throw new RuntimeException("JavaFX not detected at " + jfxJar.getPath )
        
      Attributed.blank(jfxJar)
    }
  )