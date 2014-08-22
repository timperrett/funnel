package oncue.svc.funnel.chemist


object Sharding {
  import scalaz.==>>
  import intelmedia.ws.funnel.BucketName

  type Flask = InstanceID
  type Distribution = Flask ==>> Set[Target]

  case class Target(bucket: BucketName, url: SafeURL)

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def flasks(d: Distribution): Set[Flask] =
    sorted(d).keySet

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Map[Flask, Set[Target]] =
    d.toList.sortBy(_._2.size).toMap

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[Flask, Set[Target]] =
    d.toList.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def targets(d: Distribution): Set[Target] =
    d.values.reduceLeft(_ ++ _)

  /**
   * Given the new set of urls to monitor, compute how said urls
   * should be distributed over the known flask instances
   */
  def calculate(s: Set[Target])(d: Distribution) = {
    val servers = flasks(d)
    val input   = deduplicate(s)(d)
    val is      = input.size // caching operations as its O(n)
    val foo = if(is < servers.size) servers.take(is) else servers
    // interleave the input with the known flask servers ordered by the
    // flask that currently has the least amount of work assigned.
    Stream.continually(input).flatten.zip(
      Stream.continually(foo).flatten).take(is.max(foo.size)).toList
  }

  /**
   * Given a set of inputs, check against the current known set of urls
   * that we're not already monitoring the inputs (thus ensuring that
   * the cluster is not having duplicated monitoring items)
   */
  def deduplicate(next: Set[Target])(d: Distribution): Set[Target] = {
    // get the full list of targets we currently know about
    val existing = targets(d)

    // determine if any of the supplied urls are existing targets
    val delta = next.map(_.url) &~ existing.map(_.url)

    // having computed the targets that we actually care about,
    // rehydrae a `Set[Target]` from the given `Set[SafeURL]`
    delta.foldLeft(Set.empty[Target]){ (a,b) =>
      a ++ next.filter(_.url == b)
    }
  }

}


