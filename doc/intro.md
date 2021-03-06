# Introduction to map-sql

A Clojure library designed to provide SQL-like syntax for a data structure based on maps (i.e. the "database").

The database is actually a set of maps held in an atom. Because of the set container, not duplicates maps ("records") are allowed.
The maps are assumed to have keywords as keys. Each map can have different keywords.

The provided functions aim to manipulate the database records with a SQL-like syntax.

The database can be stored to disk, load from disk with regular spit/slurp functions.


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
=> #{{:account "account1", :name "name1", :code 12345}}

;add a second record.
(in mydb insert :name "name2" :account "account2" :code 111222 :client "CL-2")
=> #{{:account "account1", :name "name1", :code 12345} {:account "account2", :name "name2", :code 111222, :client "CL-2"}}

;'update' modify records having their values changed, or added, for the given keys.
; as side-effect mydb is updated.
(in mydb where :account "account1" update :code 54321 :name "new-name")
=> #{{:account "account2", :name "name2", :code 111222, :client "CL-2"} {:account "account1", :name "new-name", :code 54321}}

;'delete-key' modify records having their keys (and associated values) removed.
; as side-effect mydb is updated.
(in mydb where :account "account2" delete-key :client :code)
=> #{{:account "account2", :name "name2"} {:account "account1", :name "new-name", :code 54321}}

;'update' can also modify records to add values for the given keys.
; as side-effect mydb is updated.
(in mydb where :account "account2" update :code 12345)
=> #{{:account "account2", :name "name2", :code 12345} {:account "account1", :name "new-name", :code 54321}}

; can 'update' several records
(in mydb update :secret true)
=> #{{:account "account2", :name "name2", :secret true, :code 12345} {:account "account1", :name "new-name", :secret true, :code 54321}}

;'rename-keys' modify records having their keys renamed with new values.
; as side-effect mydb is updated.
(in mydb rename-key :secret :public-for-nsa)
=> #{{:account "account1", :name "new-name", :code 54321, :public-for-nsa true} {:account "account2", :name "name2", :code 12345, :public-for-nsa true}}
```


'select' return records optionally filtered or ordered. Keys returned can be restricted.

```clj
;the whole database
(select from mydb)
=> #{{:account "account1", :name "new-name", :code 54321, :public-for-nsa true} {:account "account2", :name "name2", :code 12345, :public-for-nsa true}}

;specific keys
(select :name :account :code from mydb)
=> #{{:code 54321, :account "account1", :name "new-name"} {:code 12345, :account "account2", :name "name2"}}

;specific criteria with strict comparison.
(select :name :account :code from mydb where :name "new-name")
=> #{{:code 54321, :account "account1", :name "new-name"}}

;specific criteria with contain comparison.
(select :name :account :code from mydb where-contains :name "name")
=> #{{:code 54321, :account "account1", :name "new-name"} {:code 12345, :account "account2", :name "name2"}}

;several criterias with logical 'or' comparison
; :name = "name2" OR :code = 54321
(select :name :account :code from mydb where :name "name2" :code 54321)
=> #{{:code 54321, :account "account1", :name "new-name"} {:code 12345, :account "account2", :name "name2"}}

;nesting 'where' calls to perform logical 'and' comparison
; :name = "name2" AND :code = 54321
(select :name :account :code from (from db where :code 54321) where :name "name2")
=> #{}

;returned in a specific order (ascending)
(select :name :account :code from mydb where :name "name2" :code 54321 order-by :code)
=> #{{:code 12345, :account "account2", :name "name2"} {:code 54321, :account "account1", :name "new-name"}}

;multiple orders
(select :name :account from mydb where :name "name2" :code 54321 order-by :public-for-nsa :account)
=> #{{:account "account1", :name "new-name"} {:account "account2", :name "name2"}}
```

'select-screen' behaves just like 'select' but print result on screen.
'select-pdf' behaves just like 'select' but generate a PDF file.

```clj
;pretty print records on screen
(select-screen :name :account :code from mydb where :name "name2" :code 54321 order-by :code)
=> |    :name | :account | :code |
=> |----------+----------+-------|
=> |    name2 | account2 | 12345 |
=> | new-name | account1 | 54321 |

=> 2 records printed.
=> nil

;generate a PDF file with the result of the query in a table format
(select-pdf "doc.pdf" :name :account :code from mydb where :name "name2" :code 54321 order-by :code)
=> nil

;'delete' remove records from database
(in mydb where :public-for-nsa true delete)
=> #{}
```

the database can be saved to disk and load from disk with regular spit/slurp functions.

```clj
;save to disk
(spit "my-db-as-file.data" @mydb)

;load from disk
(reset! db (read-string (slurp "my-db-as-file.data")))
;or
(def new-db (atom (read-string (slurp "my-db-as-file.data"))))
```

if a validator is defined for the database, 'insert', 'update', 'delete-key' and 'rename-key'
will throw an 'IllegalStateException' exception if the conditions of the validator return false.

```clj
(defn db-validator [new-db] (if-not (empty? new-db) (apply distinct? (map :name new-db)) true))
(set-validator! mydb db-validator)

;if mydb contains #{{:name "name2", :client "CL-2", :account "account2", :code 111222}}
;trying to insert a new record with "name2" that already exists in the database...

(in mydb insert :name "name2" :client "CL-3")
=> IllegalStateException Invalid reference state

;throws an exception. mydb is unchanged.

;you can manage the exception in your code by using try / catch
(try
 (in mydb insert :name "name2" :client "CL-3")
 (catch IllegalStateException e
  (.printStackTrace e))) ;or any other instruction to resume to normal
```
