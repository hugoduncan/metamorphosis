{:doc
 {:dependencies [[codox-md "0.1.0"]]
  :codox {:writer codox-md.writer/write-docs
          :version "0.1"
          :output-dir "doc/api/0.1"}}
 :release
 {:plugins [[lein-set-version "0.2.1"]]
  :set-version
  {:updates [{:path "README.md" :no-snapshot true}]}}}
