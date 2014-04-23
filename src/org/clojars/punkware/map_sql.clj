(ns #^{:doc "SQL-like syntax for maps."
       :author "Jean-Marc Decouleur <punkware@free.fr>"
       :version "0.3.2"}
  org.clojars.punkware.map-sql
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


(defn- p-table-db
  "pretty print on screen the given keys of the records.
  FOR INTERNAL USE ONLY. Should not be called directly."
  [records & keys-to-display]
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
  "convert SQL-like syntax to regular function calls.
  FOR INTERNAL USE ONLY. Should not be called directly."
  [output args]
  (let
    [order-by-args (drop-while #(not= 'order-by %) args)
     where-args (drop-while #(not= 'from %)(take-while #(not= 'order-by %) args))
     display-args (take-while #(and (not= 'from %) (not= 'order-by %)) args)]
    `(~output
      ~(if (empty? order-by-args)
         where-args
         `(order-by ~where-args ~@(rest order-by-args)))
      ~@(if (empty? display-args) nil display-args))))


(defmacro print-db
  "same as 'select', but print the records on screen.
  e.g. (print-db :name :code from mydb where :account \"my-account\" order-by :name)"
  [& args]
    `(parse-sql #'org.clojars.punkware.map-sql/p-table-db ~args))


(defmacro select
  "return (optionally only certain keys of) records, optionally filtered by the 'where' clause, optionally ordered by the 'order-by' clause.
  e.g. (select :name :code from mydb where :account \"my-account\" order-by :name)"
  [& args]
    `(parse-sql keep-keys ~args))


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


(defn keep-keys
  "return records having only keys given in keys-to-keep.
  e.g. (keep-keys (from mydb where :account \"my-account\") :account :name)"
  ([records] records)
  ([records & keys-to-keep]
  (apply sorted-set-by (fn [_ _] 1) (map #(select-keys % keys-to-keep) records))))


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
  (try
    (swap! db p-insert (apply concat (partition 2 keys-values))) ;partition use to remove possible orphan key
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn update
  "modify records having their values changed, or added, for the given keys.
  e.g. (update mydb (where mydb :account \"my-account\") :code \"12345\")"
  [db records & keys-values]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)
         (> (count keys-values) 1)
         (every? keyword? (map first (partition 2 keys-values)))]}
  (try
    (swap! db p-modify assoc records (apply concat (partition 2 keys-values))) ;partition use to remove possible orphan key
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn delete
  "delete records.
  e.g. (delete mydb (where mydb :account \"my-account\"))"
  [db records]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)]}
  (try
    (swap! db p-delete records)
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn delete-key
  "modify records having their keys (and associated values) removed.
  e.g. (delete-key mydb (where mydb :account \"my-account\") :code)"
  [db records & keys-to-delete]
  {:pre [(or (set? records) (seq? records)) ; when 'where' return an empty seq
         (every? map? records)
         (not (empty? keys-to-delete))
         (every? keyword? keys-to-delete)]}
  (try
    (swap! db p-modify dissoc records keys-to-delete)
    (catch IllegalStateException e
      (println db-validation-error-message))))


(defn rename-key
  "modify records having their keys renamed with new values.
  e.g. (rename-key mydb (where mydb :account \"my-account\") :client :customer)"
  [db records & keys-values]
  {:pre [(or (set? records) (seq? records)) ; when 'records' return an empty seq
         (every? map? records)
         (> (count keys-values) 1)
         (every? keyword? keys-values)]}
  (try
    (swap! db p-rename records (apply concat (partition 2 keys-values)))
    (catch IllegalStateException e
      (println db-validation-error-message))))
