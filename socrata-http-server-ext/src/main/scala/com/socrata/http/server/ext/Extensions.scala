package com.socrata.http.server.ext

class Extensions private (map: Map[Extensions.Key[_], _]) {
  def +[T](kv: (Extensions.Key[T], T)) = new Extensions(map + kv)

  def get[T](key: Extensions.Key[T]): Option[T] = map.get(key).asInstanceOf[Option[T]]
}

object Extensions {
  val empty = new Extensions(Map.empty)

  final class Key[T] private[Extensions] (label: String) {
    override def toString = label
  }

  def newKey[T](label: String): Key[T] = new Key[T](label)
}
