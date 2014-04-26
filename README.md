# `map-sql`

A Clojure library designed to provide SQL-like syntax for small in-memory database.


## Releases and Dependency Information

I publish releases to [Clojars]

[Leiningen] dependency information:

    [map-sql "0.5.0"]

[Clojars]: http://clojars.org/map-sql
[Leiningen]: http://leiningen.org/

I have successfully tested 'map-sql' with Clojure version 1.5.1 and 1.6.0.


## Usage

```clj
(ns com.example.your-application
  (:require [org.clojars.punkware.map-sql :as map-sql]))
```

## Main functionalities

```clj
;create the database mydb and return it.
(def mydb (create-db))
=> #'user/mydb

;'insert' add a new record with specified keys and values. As side-effect mydb is updated.
(in mydb insert :name "name1" :account "account1" :code 12345)

;add a second record.
(in mydb insert :name "name2" :account "account2" :code 111222 :client "CL-2")

;'update' modify records having their values changed, or added, for the given keys.
; as side-effect mydb is updated.
(in mydb where :account "account1" update :code 54321 :name "new-name")

;'delete-key' modify records having their keys (and associated values) removed.
; as side-effect mydb is updated.
(in mydb where :account "account2" delete-key :client :code)

;'update' can also modify records to add values for the given keys.
; as side-effect mydb is updated.
(in mydb where :account "account2" update :code 12345)

; can 'update' several records
(in mydb update :secret true)

;'rename-keys' modify records having their keys renamed with new values.
; as side-effect mydb is updated.
(in mydb rename-key :secret :public-for-nsa)

;'delete' remove records from database.
(in mydb where :public-for-nsa true delete)
```

'select' return records optionally filtered or ordered. Keys returned can be restricted.

```clj
;the whole database
(select from mydb)

;specific keys
(select :name :account :code from mydb)

;specific criteria with strict comparison.
(select :name :account :code from mydb where :name "new-name")

;specific criteria with contain comparison.
(select :name :account :code from mydb where-contains :name "name")

;several criterias with logical 'or' comparison
(select :name :account :code from mydb where :name "name2" :code 54321)

;returned in a specific order (ascending)
(select :name :account :code from mydb where :name "name2" :code 54321 order-by :code)

;multiple orders
(select :name :account from mydb where :name "name2" :code 54321 order-by :public-for-nsa :account)
```

```clj
;pretty print records on screen in a table format.
(select-screen :name :account :code from mydb where :name "name2" :code 54321 order-by :code)

;generate a PDF file with the result of the query in a table format.
(select-pdf "doc.pdf" :name :account :code from mydb where :name "name2" :code 54321 order-by :code)
```

See the documentation for more details.

## License

Copyright © 2014 Jean-Marc Decouleur

Distributed under the Eclipse Public License, the same as Clojure.
