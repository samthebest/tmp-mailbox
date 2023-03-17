# Admissions

I've tried to stick to the crux of the problem by elucidating the core thrust of my idea, 
so I've left out code pertaining to trivial/obvious stuff nor fleshed out much of the details.

Unfortunately the code has a few comments, just to highlight things I've skipped.

I can't see an efficient, but low framework/dependency way to avoid OOP style here.

So I have not implemented

 - An HTTP API wrapper around the calls (if I did, I'd likely use naive-http)
 - Storage of events (and offset) to a log or database so can recover from crashes, or if inboxes cannot fit in memory

# Assumptions

 - It's OK to re-use previously used email addresses after they have expired
   (using extra suffixes based on 10 minute time windows, it would be fairly easy to avoid re-use)


# Design

# Performance

With a prefix list of 6 million, then we can do 6,000,000 / (10 * 60) = 10,000 TPS.

Suppose each prefix is roughly 10 bytes (say the concatenation of two words) then that's 6 * 10 = 60 MB. 
We'd have to some experiments to see if we need to chop this up into smaller arrays so that it will get put into L3 cache.
