# Clojars Stats

These are the download statistics from [clojars.org](https://clojars.org), from
since they've been kept (2012-11-01) until (roughly) the present.

You can download these directly from Clojars, but it takes a while, so I did it
once so you don't have to. I used a simply loop in bash:

```
# From S3, recommended
CLOJARS_STATS_ROOT=clojars-stats-production.s3.us-east-2.amazonaws.com

# From Clojars directly
# CLOJARS_STATS_ROOT=clojars.org/stats

# Starts from yesterday and works its way back, until it finds an EDN file that already exists. Good for backfilling the history.
i=1 ; while [[ ! -f downloads-`date -d "now - $i days" "+%Y%m%d"`.edn ]]; do wget https://${CLOJARS_STATS_ROOT}/downloads-`date -d "now - $i days" "+%Y%m%d"`.edn ; i=`expr $i + 1`; done

```

Tip: [babashka](https://github.com/borkdude/babashka) and
[jet](https://github.com/borkdude/jet) make it very easy to poke at these from
the command line.
