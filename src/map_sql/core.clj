(ns #^{:doc "SQL functions for maps."
       :author "Jean-Marc Decouleur <jm.decouleur@me.com>"
       :version "0.1.0"}
  map-sql.core
  (:require
   [clojure.pprint :refer [print-table]]
   [clojure.set :refer [difference union rename]]
   [clojure.string :refer [upper-case]]
   [inflections.core :refer [pluralize]]))

(def ^{:private true :const true}
  #^{:doc "the error message that is printed when a database validation error is detected."}
  db-validation-error-message "database validation failed, maybe because of blank record, duplicate records, or ill-formed record (no keyword / string). Insert aborted.")

; PRIVATE functions


(defn- p-delete
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db where]
  (difference db where))


(defn- p-modify
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db kindof-update where updates]
  (union
   (p-delete db where)
   (set (map #(apply kindof-update %1 updates) where))))


(defn- p-insert
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db record]
  (conj db
        (apply hash-map record)))


(defn- p-rename
  "FOR INTERNAL USE ONLY. Should not be called directly."
  [db where updates]
  (let [updates-as-hash (apply hash-map updates)]
    (union
     (p-delete db where)
     (rename where updates-as-hash))))


; PUBLIC functions and macros


(defn create-db
  "create the database and return it.
  e.g. (def mydb (create-db))"
  []
  (atom #{}))

(defmacro select
  "same as the 'display' function but with a SQL-like syntax.
  e.g. (select :name :code from mydb where :account \"my-account\" order-by :name)"
  [& args]
  (let
    [order-by-args (drop-while #(not= 'order-by %) args)
     where-args (drop-while #(not= 'from %)(take-while #(not= 'order-by %) args))
     display-args (take-while #(and (not= 'from %) (not= 'order-by %)) args)]
    `(display
      ~(if (empty? where-args) (list 'from) where-args)
      ~(if (empty? order-by-args) (list 'order-by) order-by-args)
      ~@(if (empty? display-args) nil display-args))))

(defmacro from
  "SQL-like syntax for capturing the source database.
  e.g (from mydb where :account \"my-account\")"
  [db & where-clause]
  `(~(first where-clause) ~db ~@(rest where-clause)))

(defn display
  "display records selected by where clause, sorted by order-by clause. Display specified keys or all if none specified.
  e.g. (display (where :account \"my-account\") (order-by :name) :name :code)"
  [where orderby & keys-to-display]
  (if-not (empty? where)
    (print-table
     (if (empty? keys-to-display)
       (sort (distinct (apply concat (map keys where))))
       ;;ou
       ;;(keys (into (sorted-map) where))
       keys-to-display)
     (sort-by (apply juxt orderby) where)))
  (println)
  (println (str (pluralize (count where) "record") " selected.")))

(defn where
  "filter the records according to the specified key and value. if none specified, returns all records.
  The filter is based on 'contains' comparison.
  e.g. (where mydb :account \"my-account\")"
  ([db] @db)
  ([db & keys-values]
   (set
    (apply concat
           (for [[k v] (partition 2 keys-values)]
             (filter #(re-find
                       (re-pattern (upper-case v))
                       (upper-case (get %1 k "")))
                     @db))))))

(defn where-strict
  "filter the records according to the specified key and value. if none specified, returns all records.
  The filter is based on strict comparison.
  e.g. (where mydb :account \"my-account\")"
  ([db] @db)
  ([db & keys-values]
   (set (filter #(not (not-any?
                       (set (partition 2 keys-values))
                       (seq %)))
                @db))))

(defn order-by
  "return the sorting key. To be used as an argument to the display function.
  e.g. (orderby :name)"
  ([] (list (fn [i] 0))) ;always return the same comparator value (zero) so that the database is not re-ordered.
  ([& sort-key] sort-key))

(defn insert
  "add a new record with specified keys and values.
  e. g. (insert mydb :name \"my-name\" :account \"my-account\" :code \"12345\")"
  [db & keys-values]
  {:pre [(> (count keys-values) 1)
         (every? keyword? (map first (partition 2 keys-values)))
         (every? string? (map second (partition 2 keys-values)))]}
  (try
    (swap! db p-insert (apply concat (partition 2 keys-values))) ;partition use to remove possible orphan key
    (println "1 record inserted.")
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn update
  "modify records based on where clause, having their specified value changed, or added, for the specified key.
  e.g. (update mydb (where :account \"my-account\") :code \"12345\")"
  [db where & keys-values]
  {:pre [(or (set? where) (seq? where)) ; when 'where' return an empty seq
         (every? map? where)
         (> (count keys-values) 1)
         (every? keyword? (map first (partition 2 keys-values)))
         (every? string? (map second (partition 2 keys-values)))]}
  (try
    (swap! db p-modify assoc where (apply concat (partition 2 keys-values))) ;partition use to remove possible orphan key
    (println (str (pluralize (count where) "record") " updated."))
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn delete
  "delete records base on where clause.
  e.g. (delete mydb (where :account \"my-account\"))"
  [db where]
  {:pre [(or (set? where) (seq? where)) ; when 'where' return an empty seq
         (every? map? where)]}
  (try
    (swap! db p-delete where)
    (println (str (pluralize (count where) "record") " deleted."))
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn delete-key
  "modify records based on where clause, having their specified key (and associated value) removed.
  e.g. (delete-key mydb (where :account \"my-account\") :code)"
  [db where & keys-to-delete]
  {:pre [(or (set? where) (seq? where)) ; when 'where' return an empty seq
         (every? map? where)
         (not (empty? keys-to-delete))
         (every? keyword? keys-to-delete)]}
  (try
    (swap! db p-modify dissoc where keys-to-delete)
    (println (str (pluralize (count where) "key") " deleted."))
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn rename-key
  "modify records based on where clause, having their specified key renamed with new name.
  e.g. (rename-key mydb (where:account \"my-account\") :client :customer)"
  [db where & keys-values]
  {:pre [(or (set? where) (seq? where)) ; when 'where' return an empty seq
         (every? map? where)
         (> (count keys-values) 1)
         (every? keyword? keys-values)]}
  (try
    (swap! db p-rename where (apply concat (partition 2 keys-values)))
    (println (str (pluralize (count where) "key") " renamed."))
    (catch IllegalStateException e
      (println db-validation-error-message))))

