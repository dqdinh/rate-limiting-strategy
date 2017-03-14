package com.lookout.ratelimitingfilter.models

final case class RedisError(
  message: String
) extends Exception(message)

object RedisError
