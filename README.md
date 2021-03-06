# Example Strategy for Finagle's Rate Limiting Filter

[![Build Status](https://travis-ci.org/lookout/rate-limiting-filter.svg?branch=master)](https://travis-ci.org/lookout/rate-limiting-filter)

This project contains an example of a rate limiting strategy using Finagle's built-in rate limiting filter.
We use a leaky bucket algorithm implemented using Redis as described in
["Better Rate Limiting With Redis Sorted Sets"](https://engineering.classdojo.com/blog/2015/02/06/rolling-rate-limiter/)

You can add your own custom strategy and use `LookoutRateLimitingStrategy.scala` as an example.

For more details, please see ./docs/DESIGN.org.

## Development Setup
Install Scala and SBT - http://www.scala-sbt.org/0.13/docs/Setup.html

Alternatively, you can use a self-contained sbt script:

```bash
  ./ci/bin/sbt
```

## Running Tests

```bash
  sbt test
```

## Demo
### Running an example http server with the rate limiting filter

```bash
  sbt "project example" "run HttpServer"
```

### Setup
- Http server running with ratelimit filter
- redis as the data store
- There are two rateLimitRules:
(1) Global rule on service =les= at rate of =10 requests / 60 seconds=
(2) API rule on service =les= at URL =/les= and at rate of =5 requests / 60 seconds=
- We'll call GET on /les and after 5 times, it will trigger the rate limit.
- However, calling / will still pass through, but it will rate limit after 5 times

### Run 1: Rate Limit by API and Service Name
- run `curl localhost:8080/les` times 5
- show server println
- show redis logs
- check redis console

### Run 2: Rate Limit by Service Name
- run `curl localhost:8080` times 5
- show server println
- show redis logs
- check redis console

## Motivation for open sourcing this project
We thought open sourcing this project would be useful since there is a lack of open sourced and
non-trivial Finagle rate limiting strategies.

Many Scala projects at Lookout use Finagle and this is our way of giving back to the OSS community.

## Authors
- [@dqdinh](https://github.com/dqdinh)
- [@avalade](https://github.com/avalade)
