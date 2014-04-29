(ns #^{:doc "Testing suite for map-sql"
       :author "Jean-Marc Decouleur <punkware@free.fr>"
       :version "0.5.0"}
  org.clojars.punkware.map-sql-test
  (:require [clojure.test :refer :all]
            [org.clojars.punkware.map-sql :refer :all]))


(defn db-initialize [f]
  (def db (atom #{{:name "bar" :account "bar-account" :code "bar-code"}}))
  (set-validator! db (fn [new-db] (if-not (empty? new-db) (apply distinct? (map :name new-db)) true)))
  (f))


(use-fixtures :each db-initialize)


(testing "map-sql"
  (deftest filter-records
    (is (=
         (get (first (from db where :account "bar-account")) :code)
         "bar-code")
        "a first record with :code equals to \"bar-code\" should exist.")

    (is (=
         (get (first (from db where :account "foo")) :account)
         nil)
        "a first record with :account equals to \"foo\" should not exist.")

    (is (=
         (get (first (from db where :foo "bar-account")) :code)
         nil)
        "a first record with key :foo should not exist.")

    (is (=
         (get (first (from db where-contains :account "bar")) :account)
         "bar-account")
        "a first record with key :foo should not exist.")

    (is (=
         (empty? (from db where :account "foo"))
         true)
        "there should be no records where :account is equal to \"foo\".")

    (is (=
         (empty? (from db where :account))
         true)
        "there should be no records when :account is not defined.")

    (is (=
         (get (first (from db where)) :code)
         "bar-code")
        "when 'where' is called without variadic arguments, the whole database should be returned.")

    (is (=
         (count (where (atom #{{:name "bar" :account "bar-account" :code "bar-code"} {:name "foo" :account "foo-account" :code "foo-code"}}) :name "bar" :code "bar-code"))
         1)
        "when a record matches several keys-values, the record should be returned only once."))


  (deftest ordered-records
    (is (=
         (:code (last (order-by #{{:name "foo1" :account "foo-account1" :code "foo-code1"} {:name "foo2" :account "foo-account2" :code "foo-code2"} {:name "foo1" :account "foo-account2" :code "foo-code3"}} :code)))
         "foo-code3")
        "records sould be ordered by :code ascending")

    (is (=
         (:code (last (order-by #{{:name "foo1" :account "foo-account1" :code "foo-code1"} {:name "foo2" :account "foo-account2" :code "foo-code2"} {:name "foo1" :account "foo-account2" :code "foo-code3"}} :name)))
         "foo-code2")
        "records sould be ordered by :name ascending")

    (is (=
         (:code (last (order-by #{{:name "foo1" :account "foo-account2" :code "foo-code1"} {:name "foo1" :account "foo-account1" :code "foo-code3"}} :name :account)))
         "foo-code1")
        "records sould be ordered by :name :account ascending"))


  (deftest select-records
    (is (=
         (select from (atom #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}}))
         #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}})
        "should reurn the whole database.")

    (is (=
         (select from (atom #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}}) where :b 1 )
         #{{:a 1, :c 3, :b 1}})
        "should return only record where b = 1.")

    (is (=
         (select from (atom #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}}) where :a 1 )
         #{{:a 1, :c 3, :b 1} {:a 1, :c 2, :b 3}})
        "should return only records where a = 1.")

    (is (=
         (sort (distinct (apply concat (map keys (select :b :c from (atom #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}}))))))
         [:b :c])
        "should return only :b & :c keys.")

    (is (=
         (:b (last (select :b :c from (atom #{{:a 1, :c 2, :b 3} {:a 3, :c 1, :b 2} {:a 1, :c 3, :b 1}}) where :b 1 :a 1 order-by :b)))
         3)
        "should return only :b & :c keys for records where a = 1 or b =1 ordered by b ascending."))


  (deftest insert-record
    (is (thrown? AssertionError (in db insert))
        "insert called without arguments should throw an exception.")

    (is (thrown? AssertionError (in db insert :name))
        "insert called with only one argument should throw an exception.")

    (is (thrown? AssertionError (in db insert "name" "foo"))
        "insert called with a key not being a keyword should throw an exception.")

    (is (=
         (count (in db insert :name "foo" :account "foo-account" :code "foo-code"))
         2)
        "after inserting a second different record, there should be 2 records in the database.")

    (is (thrown? IllegalStateException (insert db :name "bar" :account "foo-account" :code "foo-code"))
        "after inserting a record causing validator to fail, there should still be 2 records in the database."))


  (deftest update-records
    (is (thrown? AssertionError (in db update))
        "update called without arguments should throw an exception.")

    (is (thrown? AssertionError (in db update :name))
        "update called with only one argument should throw an exception.")

    (is (thrown? AssertionError (in db update "name" "foo"))
        "update called with a key not being a keyword should throw an exception.")

    (is (thrown? AssertionError (in #{[1 2]} update :name "foo"))
        "update called with a where clause not returning a set of maps should throw an exception.")

    (is (thrown? AssertionError (in [{:name "bar" :account "bar-account" :code "bar-code"}] update :name "foo"))
        "update called with a where clause not returning a set should throw an exception.")

    (is (=
         (get (first (in db where :name "foo" update :code "mynewcode")) :code)
         "bar-code")
        "after trying to update a record that doesn't exist, the old value should still be there.")

    (is (=
         (get (first (in db where :name "bar" update :code "mynewcode")) :code)
         "mynewcode")
        "after updating the record, :code should be equal to \"mynewcode\"."))


  (deftest delete-keys
    (is (thrown? AssertionError (in db delete-key))
        "delete-key called without arguments should throw an exception.")

    (is (thrown? AssertionError (in db delete-key "name"))
        "delete-key called with a key not being a keyword should throw an exception.")

    (is (thrown? AssertionError (in #{[1 2]} delete-key :name))
        "delete-key called with a where clause not returning a set of maps should throw an exception.")

    (is (thrown? AssertionError (in [{:name "bar" :account "bar-account" :code "bar-code"}] delete-key :name))
        "delete-key called with a where clause not returning a set should throw an exception.")

    (is (=
         (get (first (in db where :name "bar" delete-key :code2)) :code)
         "bar-code")
        "after trying to delete an unexisting key in the first record, :code should still be there.")

    (is (=
         (get (first (in db where :name "bar" delete-key :code)) :code)
         nil)
        "after deleting the :code key in the first record, :code should not exist anymore."))


  (deftest rename-keys
    (is (thrown? AssertionError (in db rename-key))
        "rename-key called without arguments should throw an exception.")

    (is (thrown? AssertionError (in db rename-key :name))
        "rename-key called with only one argument should throw an exception.")

    (is (thrown? AssertionError (in db rename-key "name" "foo"))
        "rename-key called with a key not being a keyword should throw an exception.")

    (is (thrown? AssertionError (in #{[1 2]} rename-key :name "foo"))
        "rename-key called with a where clause not returning a set of maps should throw an exception.")

    (is (thrown? AssertionError (in [{:name "bar" :account "bar-account" :code "bar-code"}] rename-key :name "foo"))
        "rename-key called with a where clause not returning a set should throw an exception.")

    (is (=
         (get (first (in db where :name "bar" rename-key :test :fail)) :fail)
         nil)
        "after trying to rename a key that doesn't exist in the first record, the renamed key should not exist.")

    (is (=
         (get (first (in db where :name "bar" rename-key :code :password)) :code)
         nil)
        "after renaming a key in the first record, :code should not exist anymore.")

    (is (=
         (get (first (in db where :name "bar" rename-key :code :password)) :password)
         "bar-code")
        "after renaming a key in the first record, :code should now be named :password."))


  (deftest delete-records
    (is (thrown? AssertionError (in #{[1 2]} delete))
        "delete called with a where clause not returning a set of maps should throw an exception.")

    (is (thrown? AssertionError (in [{:name "bar" :account "bar-account" :code "bar-code"}] delete))
        "delete called with a where clause not returning a set should throw an exception.")

    (is (=
         (count (in db where :name "foo" delete))
         1)
        "after trying to delete a record that doesn't exist, there should still be one record in the database.")

    (is (=
         (count (in db where :name "bar" delete))
         0)
        "after deleting the record, there should be zero record in the database.")))

