(defproject map-sql "0.5.1"
  :description "SQL-like syntax for maps."
  :url "https://github.com/PunkWare/map-sql"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [inflections "0.9.6"]
                 [clj-pdf "1.11.17"]]
  :url "https://github.com/PunkWare/map-sql"
  :scm {:name "git"
        :url "https://github.com/PunkWare/map-sql"}
  :deploy-repositories [["clojars" {:creds :gpg}]])
