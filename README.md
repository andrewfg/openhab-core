# openHAB Core

[![GitHub Actions Build Status](https://github.com/openhab/openhab-core/actions/workflows/ci-build.yml/badge.svg?branch=main)](https://github.com/openhab/openhab-core/actions/workflows/ci-build.yml)
[![Jenkins Build Status](https://ci.openhab.org/job/openHAB-Core/badge/icon)](https://ci.openhab.org/job/openHAB-Core/)
[![JavaDoc Build Status](https://ci.openhab.org/view/Documentation/job/openHAB-JavaDoc/badge/icon?subject=javadoc)](https://ci.openhab.org/view/Documentation/job/openHAB-JavaDoc/)
[![EPL-2.0](https://img.shields.io/badge/license-EPL%202-green.svg)](https://opensource.org/licenses/EPL-2.0)
[![Crowdin](https://badges.crowdin.net/openhab-core/localized.svg)](https://crowdin.com/project/openhab-core)

This project contains core bundles of the openHAB runtime.

Building and running the project is fairly easy if you follow the steps detailed below.

Please note that openHAB Core is not a product itself, but a framework to build solutions on top.
It is picked up by the main [openHAB distribution](https://github.com/openhab/openhab-distro).

This means that what you build is primarily an artifact repository of OSGi bundles that can be used within smart home products.

## 1. Prerequisites

The build infrastructure is based on Maven. 
If you know Maven already then there won't be any surprises for you. 
If you have not worked with Maven yet, just follow the instructions and everything will miraculously work ;-)

What you need before you start:

- Java SE Development Kit 21
- Maven 3 from https://maven.apache.org/download.html

Make sure that the `mvn` command is available on your path

## 2. Checkout

Checkout the source code from GitHub, e.g. by running:

```
git clone https://github.com/openhab/openhab-core.git
```

## 3. Building with Maven

To build this project from the sources, Maven takes care of everything:

- set `MAVEN_OPTS` to `-Xms512m -Xmx1024m`
- change into the openhab-core directory (`cd openhab-core`)
- run `mvn clean spotless:apply install` to compile and package all sources

If there are tests that are failing occasionally on your local build, run `mvn -DskipTests=true clean install` instead to skip them.

For an even quicker build, run `mvn clean install -T1C -DskipChecks -DskipTests -Dspotless.check.skip=true`.

## How to contribute

If you want to become a contributor to the project, please read about [contributing](https://www.openhab.org/docs/developer/contributing.html) and check our [guidelines](https://www.openhab.org/docs/developer/guidelines.html) first.

