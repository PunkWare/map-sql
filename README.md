# map-sql

A Clojure library designed to provide SQL-like functions (and macros) for data structure based on maps.

## Install

With Leiningen:

``` clj
[map-sql "0.1.0"]
```

## Usage

```clj
(require 'map-sql.core)

;create the database and return it.
(def mydb (create-db))

;add a new record with specified keys and values.
(insert mydb :name "my-name" :account "my-account" :code "12345")

;modify records based on where keys-values, having their specified value changed, or added, for the specified key.
(update mydb (where :account "my-account") :code "54321")

;modify records based on where keys-values, having their specified key (and associated value) removed.
(delete-key mydb (where :account "my-account") :code)

;modify records based on where keys-values, having their specified key renamed with new name.
(rename-key mydb (where :account "my-account") :client :customer)

;delete records base on where clause.
(delete mydb (where :account "my-account"))

;display records selected by where keys-values, sorted by order-by clause. Display specified keys or all if none specified.
(display (where :account "my-account") (order-by :name) :name :code)

;or with a more friedly syntax using the select macro:
(select :name :code from mydb where :account "my-account" order-by :name)
```

## License

Copyright © 2014 Jean-Marc Decouleur

Distributed under the Eclipse Public License, the same as Clojure.
