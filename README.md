# Summary

Hope I haven't missed the point of the exercise. 
I've tried to stick to the crux of the problem, which is generating easy to remember unique emails with
potentially high TPS and in a thread safe way.  To elucidate the core thrust of my idea, I've left out code
pertaining to trivial/obvious stuff nor fleshed out much of the details.

## Create easy to remember unique email:

**Single Node:**

**Burst TPS**: 1,000,000 (easily, but ignoring HTTP wrapper overhead)

**Sliding window average TPS**: 10,000 (easily, but ignoring HTTP wrapper overhead)

Distributing: trivial by design, can beat the above TPS if necessary

Upper limit: Likely only the practicalities of HTTP and load balancing, though eventually memory and L3 cache will constrain

# Admissions

I can't see an efficient, but low framework/dependency way to avoid OOP style here.

I have not implemented:

 - An HTTP API wrapper around the calls (if I did, I'd likely use naive-http server)
 - Storage of emails in a log/database so can recover from crashes, or if inboxes cannot fit in memory

# Assumptions

 - It's OK to re-use previously used email addresses after they have expired
   (using suffixes based on 10 minute time windows, it would be fairly easy to avoid re-use)
 - We want to optimise for creating inboxes, since this is the only part that really needs global consistency.
   Optimising for adding and getting emails is trivial if we solve this well.
 - The current logic assumes all prefixes are the same length for simplicity.  (Fixing this is just a case of adding
   a couple of hash maps for storing indexes/lengths or something)

# Design

Pre-generate a huge list of easy to remember email address prefixes before application launch, 
at launch load this store in memory (or even L3 cache), 
then cycle around them.

## Minimise Blocking Logic

The only part of the core logic that needs to block is really trivial, and so easy to run concurrently at high TPS

The blocking part is just incrementing a number (pointer), a call to `System.currentTimeMillis()`,
and slicing an array, stuff like that.

## Unique Emails For Long Time

Optionally could use 10 minute time blocks to lookup easy to remember suffixes to make emails unique for all time,
e.g. the time window of (01/01/2024 00:00:00 to 01/01/2024 00:00:10) could map to "blue-roger"
we only need 1,000,000 of these to support 20 years of runtime.

## Disaster Recovery, Storage to Disk, Sharding and Blue-Green Deploys

 - **Distributing:** This is trivial, just choose disjoint prefix lists for each node and 
  load balancers (LB) can route requests.
 - **Blue-Green Deploys:** _Then_ we get blue-green deployment as a corollary; let the LB route no `Create` requests
   to the old instance(s) for 20 minutes, after which time they can be shut down and only new instance(s) running
 - **Storage to Disk for HA/DR:** _So_ we just need to store `Store Email` in a log/database, and `Create` command can stay 
   purely in-memory.
 - **YAGNI DR?**: Get the basics stable (like don't use Azure!) and the infrequency of outages may mean  
   negligible business impact to just doing a clean bounce after 10 minutes.

# Create Inbox Performance

## Underlying API Class

Given a prefix list of more than 1 million then **burst TPS = 1,000,000**

Given a prefix list of 6 million, then max **sliding window average TPS** 6,000,000 / (10 * 60) = 10,000.

### Memory

Suppose each prefix is roughly 10 bytes (say the concatenation of two words) then that's 6 * 10 = 60 MB. 
We'd have to do some experiments to see if we need to chop this up into smaller arrays so that it will get put into L3 cache.

## Through an HTTP API

In practice since a http API would wrap the class, the rate of serving http requests would become the bottleneck and
1,000,000 TPS would be extremely unlikely, but on a couple of machines 10,000 TPS seems reasonable.

## Upper Limitations

For astronomical prefix lists, eventually memory will become a concern, but the practicalities of HTTP and load balancing
would constrain way before the theoretical limit.

# README

Ensure `sbt` installed, say `brew install sbt`. Tests: `sbt test`. TODO Entry point