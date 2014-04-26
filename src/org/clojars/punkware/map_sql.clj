(ns #^{:doc "SQL-like syntax for maps."
       :author "Jean-Marc Decouleur <punkware@free.fr>"
       :version "0.5.0"}
  org.clojars.punkware.map-sql
  (:require
   [clojure.pprint :refer [print-table]]
   [clojure.set :refer [difference union rename]]
   [clojure.string :refer [upper-case]]
   [inflections.core :refer [pluralize]]
   [clj-pdf.core :refer [pdf]]))


; PRIVATE functions


(defn- p-delete
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db records]
  (difference db records))


(defn- p-modify
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db kindof-update records updates]
  (union
   (p-delete db records)
   (set (map #(apply kindof-update %1 updates) records))))


(defn- p-insert
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db record]
  (conj db
        (apply hash-map record)))


(defn- p-rename
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db records updates]
  (let [updates-as-hash (apply hash-map updates)]
    (union
     (p-delete db records)
     (rename records updates-as-hash))))


(defn- table-screen
  "pretty print on screen the given keys of the records.
  FOR INTERNAL USE ONLY. Should not be called directly."
  [records keys-to-display]
  (if-not (empty? records)
    (print-table
     (if (empty? keys-to-display)
       (sort (distinct (apply concat (map keys records))))
       ;;ou
       ;;(keys (into (sorted-map) records))
       keys-to-display)
     records))
  (println)
  (println (str (pluralize (count records) "record") " printed.")))


(defn- pdf-table
  "format the table in the PDF.
  FOR INTERNAL USE ONLY. Should not be called directly."
  [headers column-widths rows]
  (into [] (concat
            [:pdf-table column-widths]
            [(mapv #(vector :pdf-cell [:paragraph {:style :bold :align :center} (name %)]) headers)]
            (mapv #(mapv (fn [element] [:pdf-cell (if (integer? element) {:align :right} {:align :left})(str element)]) %) rows))))


(defn- pdf-main
  "set the PDF properties and calculate table's cells width (relative %).
  FOR INTERNAL USE ONLY. Should not be called directly."
  [records headers-raw pdf-file]
  (let [headers (if (empty? headers-raw)
                  (sort (distinct (apply concat (map keys records))))
                  headers-raw)]
    (pdf
     [{:title "map-sql" :size :a4 :orientation :landscape}
      (pdf-table
       headers
       (into []
             (let [sizes (for [x headers]
                           (apply max
                                  (map #(if (integer? (x %)) 5 (count (x %)))
                                       records)))
                   total (reduce + sizes)]
               (map
                #(Math/round
                  (* 100.0 (/ % total)))
                sizes)))
       (mapv #(for [k headers] (get % k ""))
             records))]
     pdf-file)))


; PUBLIC functions and macros


(defn create-db
  "create the database and return it.
  e.g. (def mydb (create-db))"
  []
  (atom #{}))


(defmacro from
  "SQL-like syntax for capturing the source database.
  e.g. (from mydb where :account \"my-account\")"
  [db & where-clause]
  `(~(if (empty? where-clause) 'where (first where-clause)) ~db ~@(rest where-clause)))


(defmacro parse-sql
  "convert SQL-like syntax to regular query function calls.
  FOR INTERNAL USE ONLY. Should not be called directly."
  [output args]
  (let
    [order-by-args (drop-while #(not= 'order-by %) args)
     where-args (drop-while #(not= 'from %)(take-while #(not= 'order-by %) args))
     display-args (take-while #(and (not= 'from %) (not= 'order-by %)) args)]
    `(~@output
      ~(if (empty? order-by-args)
         where-args
         `(order-by ~where-args ~@(rest order-by-args)))
      ~(if (empty? display-args) [] (vec display-args)))))


(defmacro select-screen
  "same as 'select', but print the records on screen.
  e.g. (select-screen :name :code from mydb where :account \"my-account\" order-by :name)"
  [& args]
  `(parse-sql [#'org.clojars.punkware.map-sql/table-screen] ~args))


(defmacro select
  "return (optionally only certain keys of) records, optionally filtered by the 'where' clause, optionally ordered by the 'order-by' clause.
  e.g. (select :name :code from mydb where :account \"my-account\" order-by :name)"
  [& args]
  `(parse-sql [#'org.clojars.punkware.map-sql/keep-keys] ~args))


(defmacro select-pdf
  "same as 'select', but generate a PDF file with the result of the query in a table format.
  e.g. (select-pdf \"doc.pdf\" :name :code from mydb where :account \"my-account\" order-by :name)"
  [filename & args]
  `((parse-sql [partial #'org.clojars.punkware.map-sql/pdf-main] ~args) ~filename))


(defmacro in
  "convert SQL-like syntax to regular updating function calls."
  [& args]
  (let
    [fn-args (drop-while #(and (not= 'update %) (not= 'insert %) (not= 'delete %) (not= 'delete-key %) (not= 'rename-key %)) args)
     where-args (drop-while #(and (not= 'where %) (not= 'where-contains %)) (take-while #(and (not= 'update %) (not= 'insert %) (not= 'delete %) (not= 'delete-key %) (not= 'rename-key %)) args))
     ;where (if-not (empty? where-args) (list (list* (first where-args) (first args) (rest where-args))) '())]
     where (if (= (first fn-args) 'insert)
             '()
             (if-not (empty? where-args)
               (list (list* (first where-args) (first args) (rest where-args)))
               (list (list 'where (first args)))))]
    `(~(first fn-args) ~(first args)
       ~@where
       ~@(when-not (empty? (rest fn-args)) (rest fn-args)))))


(defn keep-keys
  "return records having only keys given in keys-to-keep.
  e.g. (keep-keys (from mydb where :account \"my-account\") :account :name)"
  ([records] records)
  ([records keys-to-keep]
   (if (empty? keys-to-keep)
     records
     (apply sorted-set-by (fn [_ _] 1) (map #(select-keys % keys-to-keep) records)))))


(defn where-contains
  "filter the records in database according to the specified keys and values. if none specified, returns all records.
  The filter is based on 'contains' comparison and do logical 'or' comparison between keys.
  e.g. (where-contains mydb :account \"account\")"
  ([database] (if (= (type database) clojure.lang.Atom)
                @database
                database))
  ([database & keys-values]
   (set
    (apply concat
           (for [[k v] (partition 2 keys-values)]
             (filter #(re-find
                       (re-pattern (upper-case v))
                       (upper-case (get %1 k "")))
                     (if (= (type database) clojure.lang.Atom)
                       @database
                       database)))))))


(defn where
  "filter the records in database according to the specified keys and values. if none specified, returns all records.
  The filter is based on strict comparison and do logical 'or' comparison between keys.
  e.g. (where- mydb :account \"my-account\")"
  ([database] (if (= (type database) clojure.lang.Atom)
                @database
                database))
  ([database & keys-values]
   (set (filter #(not (not-any?
                       (set (partition 2 keys-values))
                       (seq %)))
                (if (= (type database) clojure.lang.Atom)
                  @database
                  database)))))


(defn order-by
  "return records ordered according to sort-keys.
  e.g. (order-by (from mydb where :account \"my-account\") :name)"
  ([records] records)
  ([records & sort-keys] (apply sorted-set-by (fn [_ _] 1) (sort-by (apply juxt sort-keys) records))))


(defn insert
  "add a new record with specified keys and values.
  e. g. (insert mydb :name \"my-name\" :account \"my-account\" :code \"12345\")"
  [db & keys-values]
  {:pre [(> (count keys-values) 1)
         (every? keyword? (map first (partition 2 keys-values)))]}
  (swap! db p-insert (apply concat (partition 2 keys-values)))) ;partition use to remove possible orphan key


(defn update
  "modify records having their values changed, or added, for the given keys.
  e.g. (update mydb (from mydb where :account \"my-account\") :code \"12345\")"
  [db records & keys-values]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)
         (> (count keys-values) 1)
         (every? keyword? (map first (partition 2 keys-values)))]}
  (swap! db p-modify assoc records (apply concat (partition 2 keys-values)))) ;partition use to remove possible orphan key


(defn delete
  "delete records.
  e.g. (delete mydb (from mydb where :account \"my-account\"))"
  [db records]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)]}
  (swap! db p-delete records))


(defn delete-key
  "modify records having their keys (and associated values) removed.
  e.g. (delete-key mydb (from mydb where :account \"my-account\") :code)"
  [db records & keys-to-delete]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)
         (not (empty? keys-to-delete))
         (every? keyword? keys-to-delete)]}
  (swap! db p-modify dissoc records keys-to-delete))


(defn rename-key
  "modify records having their keys renamed with new values.
  e.g. (rename-key mydb (from mydb where :account \"my-account\") :client :customer)"
  [db records & keys-values]
  {:pre [(or (set? records) (seq? records)) ; when 'records' return an empty seq
         (every? map? records)
         (> (count keys-values) 1)
         (every? keyword? keys-values)]}
  (swap! db p-rename records (apply concat (partition 2 keys-values))))
