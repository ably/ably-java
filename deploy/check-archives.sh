#!/bin/bash

ABLY_HOME="${0%/*}/.."
ABLY_VERSION=`grep version "${ABLY_HOME}/build.gradle" | sed $'s/.*\'\(.*\)\'.*/\\\1/'`

case "${ABLY_VERSION}" in
	[0-9][0-9.]*|[0-9][0-9.]*-beta|[0-9][0-9.]*-pre)
		;;
	*)
		echo "Incorrect version: ${ABLY_VERSION}"
		exit 1
		;;
esac

ABLY_JAVA_RELEASE_DIR="${ABLY_HOME}/java/build/release/${ABLY_VERSION}/io/ably/ably-java/${ABLY_VERSION}"
ABLY_ANDROID_RELEASE_DIR="${ABLY_HOME}/android/build/release/${ABLY_VERSION}/io/ably/ably-android/${ABLY_VERSION}"

if [ ! -d "${ABLY_JAVA_RELEASE_DIR}" ]; then
	echo "Directory for Java release ${ABLY_JAVA_RELEASE_DIR} doesn't exist"
	exit 1
fi

if [ ! -d "${ABLY_ANDROID_RELEASE_DIR}" ]; then
	echo "Directory for Android release ${ABLY_ANDROID_RELEASE_DIR} doesn't exist"
	exit 1
fi

ABLY_JAVA_JAR="${ABLY_JAVA_RELEASE_DIR}/ably-java-${ABLY_VERSION}.jar"
if [ `unzip -v ${ABLY_JAVA_JAR} | grep BuildConfig | wc -l` -ne 1 ]; then
	echo "Java release failed BuildConfig test"
	exit 1
fi

echo "All tests passed"

