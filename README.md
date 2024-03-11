# MatchScope
MatchScope is an accurate and efficient tool for matching app versions against obufuscation

### Usage
+ To build the jar file, run `./gradlew jar`
+ To run with two versions:
  `java -jar MatchScope.jar -s [sdk_path] -w [wordlist_path] -p [apk1_path] [apk2_path]`