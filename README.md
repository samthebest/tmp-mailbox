# Summary

## Create easy to remember unique email:

**Single Node:**
**Burst TPS**: 1,000,000 (easily, ignoring HTTP wrapper overhead)
**Sliding window average TPS**: 10,000 (easily)

Distributing: trivial by design, can beat the above TPS if necessary

Upper limit: Likely only the practicalities of HTTP and load balancing, though eventually memory and L3 cache will constrain

# Admissions

I've tried to stick to the crux of the problem, which is generating easy to remember unique emails with 
potentially high TPS and in a thread safe way.  To elucidate the core thrust of my idea, I've left out code 
pertaining to trivial/obvious stuff nor fleshed out much of the details.

I can't see an efficient, but low framework/dependency way to avoid OOP style here.

So I have not implemented

 - An HTTP API wrapper around the calls (if I did, I'd likely use naive-http)
 - Storage of events (and offset) to a log or database so can recover from crashes, or if inboxes cannot fit in memory

# Assumptions

 - It's OK to re-use previously used email addresses after they have expired
   (using suffixes based on 10 minute time windows, it would be fairly easy to avoid re-use)
 - We want to optimise for creating inboxes, since this is the only part that really needs global consistency.
   Optimising for adding and getting emails is trivial after we solve this problem.


# Design

Pre-generate a huge list of easy to remember email address prefixes before application launch, 
at launch load this store in memory (or even L3 cache), 
then loop around them,

## Minimise Blocking Logic

The only part of the logic that needs to block is really trivial, and thus easy to run concurrently at high TPS

The blocking part is just incrementing a number (pointer), a call to `System.currentTimeMillis()`, some string ops,
and slicing an array, stuff like that.

## Unique Emails For All Time

Optionally could use 10 minute time blocks to lookup easy to remember suffixes to make emails unique for all time,
e.g. the time window of (01/01/2000 00:00:00 to 01/01/2000 00:00:10) could map to "blue-roger"
we only need 1,000,000 of these to support 20 years of runtime.

# Create Inbox Performance

## Underlying API Class

Given a prefix list of more than 1 million then **burst TPS == 1,000,000**

Given a prefix list of 6 million, then max **sliding window average** 6,000,000 / (10 * 60) = 10,000 **TPS**.

Suppose each prefix is roughly 10 bytes (say the concatenation of two words) then that's 6 * 10 = 60 MB. 
We'd have to some experiments to see if we need to chop this up into smaller arrays so that it will get put into L3 cache.

## Through a HTTP API

In practice since a http API would wrap the class, the rate of serving http requests would become the bottleneck and
1,000,000 TPS would be extremely unlikely, but one a single machine 10,000 TPS seems reasonable.

## Distributing

This is trivial, just choose disjoint prefix lists for each node and load balancers can route requests.

## Upper Limitations

For atronomical prefix lists, eventually memory will become a concern, so the practicalities of HTTP and load balancing
would constrain way before the theoretical limit.
