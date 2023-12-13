package uk.gov.homeoffice.bsonreader

import scala.util.Try

class ArgParser(args :List[String]) {

  var positionalArgsSeen :List[String] = Nil

  // use this for stand alone arguments and flags. e.g
  //
  // ./sudo -i
  //
  // git push --no-ff
  //
  // docker -rm    #recognises r and m smashed together here!
  //
  // ./myApp --interactive
  //
  //  How to use:
  //
  //   optionExists("-i", "--interactive")
  //   optionExists("-r", "--remove")       # in this example, -rm would trigger true for both

  def isFlagSet(anyName :String*) :Boolean = args.contains(anyName)

  // Use valueBehindOption to extract options with arguments
  // e.g.
  //
  // ./myApp -c configFile.yaml -f filename.haccon

  def valueBehindOption(lookupNames :String*) :ArgValue = {
    positionalArgsSeen = positionalArgsSeen ++ lookupNames.toList
    lookupNames.flatMap { name => args.indexOf(name) match {
      case -1 => None
      case idx => args.lift(idx+1) match {
        case Some(argChain) if argChain.startsWith("-") => None
        case Some(argValue) => Some(ArgValue(name, argValue))
        case None => None
      }
    }}.headOption.getOrElse(ArgValue("", ""))
  }
  
  // any commands that don't start with - or --
  // and don't come directly after a position arg.
  //
  // e.g.
  //
  // docker -rm run imageX       (run and image returned)
  //
  // docker build -f Dockerfile.x -t .      (pass in -f flag and t flag, so it returns build, but not Dockerfile.x and .
  def getCommands() :List[String] =
    args.filterNot(_.startsWith("-"))


}

class ArgValue(fromCommand :String, raw :String) {

  def isEmpty() :Boolean = raw.isEmpty

  def asStr() :String = raw
  def asInt() :Int = raw.toInt
  def asDouble() :Double = raw.toDouble

  def orElse(default :String) :String = if (raw.isEmpty) default else raw
  def orElseInt(default :Int) :Int = if (raw.isEmpty) default else raw.toInt
  def orElseDouble(default :Double) :Double = if (raw.isEmpty) default else raw.toDouble
  
  def isPathValid() :Boolean = java.nio.file.Files.exists(java.nio.file.Paths.get(raw))
}
