rm -rf ./android/*

./gradlew java:testRealtimeSuite
./gradlew java:testRestSuite
