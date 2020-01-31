# Clojars Stats

These are the download statistics from [clojars.org](https://clojars.org), from
since they've been kept (2012-11-01) until (roughly) the present.

You can download these directly from Clojars, but it takes a while, so I did it
once so you don't have to. I used a simply loop in bash:

```
# From S3, recommended
i=0 ; while true; do i=`expr $i + 1`; wget https://clojars-stats-production.s3.us-east-2.amazonaws.com/downloads-`date -d "now - $i days" "+%Y%m%d"`.edn ; done

# From Clojars directly
i=0 ; while true; do i=`expr $i + 1`; wget https://clojars.org/stats/downloads-`date -d "now - $i days" "+%Y%m%d"`.edn ; done
```

Tip: [babashka](https://github.com/borkdude/babashka) and
[jet](https://github.com/borkdude/jet) make it very easy to poke at these from
the command line.
