# Introduction to map-sql

A Clojure library designed to provide SQL-like functions (and macros) for a data structure based on maps (i.e. the "database").
The library is designed to be used with the REPL.

The database is actually a set of maps help in an atom. Because of the set container, not duplicates maps ("records") are allowed.
The maps are assumed to have keywords as keys. Each map can have different keywords.

The provided functions aim to manipulate the database records with a SQL-like syntax.

The database can be stored to disk, load from disk with regular spit/slurp functions.


## Usage

```clj
(ns com.example.your-application
  (:require [org.clojars.punkware.map-sql :as map-sql]))
```

### Main functionalities

```clj
;create the database and return it.
(def mydb (create-db))

;add a new record with specified keys and values.
(insert mydb :name "my-name" :account "my-account" :code "12345")

;modify records based on where keys-values, having their specified value changed, or added, for the specified key.
(update mydb (where mydb :account "my-account") :code "54321")

;modify records based on where keys-values, having their specified key (and associated value) removed.
(delete-key mydb (where mydb :account "my-account") :code)

;modify records based on where keys-values, having their specified key renamed with new name.
(rename-key mydb (where mydb :account "my-account") :code :password)

;delete records based on where clause.
(delete mydb (where mydb :account "my-account"))

;where with fuzzy comparison
(where mydb :account "account")

;where with strict comparison
(where-strict mydb :account "my-account")

;display records selected by where keys-values, sorted by order-by clause. Display specified keys or all if none specified.
(display (where mydb :account "my-account") (order-by :name) :name :password)

;or with a more friedly syntax using the select macro:
(select :name :password from mydb where :account "my-account" order-by :name)
```

Other valid requests:

```clj
;diplay all keys
(select from mydb where :account "my-account" order-by :name :code)

;no order-by defined, fuzzy comparison
(select from mydb where :account "my-account")

;no order-by defined, strict comparison
(select from mydb where-strict :account "my-account")

;filter on :account "my-account" OR :account "other-account"
(select from mydb where :account "my-account" :account "other-account")

;return the whole database
(select from mydb)
```

The database can then be saved to disk and load from disk with regular spit/slurp functions.

```clj
;save to disk
(spit "my-db-as-file.data" @mydb)

;load from disk
(reset! db (read-string (slurp "my-db-as-file.data")))
;or
(def new-db (atom (read-string (slurp "my-db-as-file.data"))))
```
