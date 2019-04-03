#!/bin/sh
VERSION=$1
TAG=$2
git checkout -b release-${TAG}
mvn versions:set -DnewVersion=${TAG}
sed -i "/release\.version=/ s/=.*/=${VERSION}/" pom.properties
git status
echo "Press ENTER to commit ${TAG}"
read
git commit -a -m "Update version to ${VERSION} and maven version to ${TAG}"
echo "Press ENTER to push the ${VERSION}, ${TAG} branch to GitHub"
read
git push -u origin release-${TAG}
