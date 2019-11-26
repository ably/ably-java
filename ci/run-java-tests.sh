# We unset this, otherwise gradlew picks up settings also from the android "context", like here: https://travis-ci.org/ably/ably-java/jobs/353969106#L1980
unset ANDROID_HOME

ret=0
./gradlew runUnitTests || ret=1
./gradlew java:testRealtimeSuite || ret=1
./gradlew java:testRestSuite || ret=1
exit $ret
