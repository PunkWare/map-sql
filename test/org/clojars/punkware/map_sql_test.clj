(ns #^{:doc "Testing suite for map-sql.core"
       :author "Jean-Marc Decouleur <jm.decouleur@me.com>"
       :version "0.1.0"}
  org.clojars.punkware.map-sql-test
  (:require [clojure.test :refer :all]
            [org.clojars.punkware.map-sql :refer :all]))

(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
     ~@tests))

(defn db-initialize [f]
  (def db #{{:name "bar" :account "bar-account" :code "bar-code"}})
  (f))

(use-fixtures :once db-initialize)

(testing "map-sql.core"
  (deftest filter-record
    (is (=
         (get (first (where (atom db) :account "bar-account")) :code)
         "bar-code")
        "a first record with :code equals to \"bar-code\" should exist.")

    (is (=
         (get (first (where (atom db) :account "foo")) :account)
         nil)
        "a first record with :account equals to \"foo\" should not exist.")

    (is (=
         (get (first (where (atom db) :foo "bar-account")) :code)
         nil)
        "a first record with key :foo should not exist.")

    (is (=
         (empty? (where (atom db) :account "foo"))
         true)
        "there should be no records where :account is equal to \"foo\".")

    (is (=
         (empty? (where (atom db) :account))
         true)
        "there should be no records when :account is not defined.")

    (is (=
         (get (first (where (atom db))) :code)
         "bar-code")
        "when 'where' is called without variadic arguments, the whole database should be returned.")

    (is (=
         (count (where (atom #{{:name "bar" :account "bar-account" :code "bar-code"} {:name "foo" :account "foo-account" :code "foo-code"}}) :name "bar" :code "bar-code"))
         1)
        "when a record matches several keys-values, the record should be returned only once."))

  (with-private-fns [org.clojars.punkware.map-sql [p-insert p-modify p-delete p-rename]]

    (deftest insert-record
      (is (=
           (count (p-insert db [:name "foo" :account "foo-account" :code "foo-code"]))
           2)
          "after inserting a second different record, there should be 2 records in the database."))

    (deftest update-record
      (is (=
           (get (first (p-modify db assoc (where (atom db) :name "bar") '(:code "mynewcode"))) :code)
           "mynewcode")
          "after updating the first record, :code should be equal to \"mynewcode\".")

      (is (=
           (get (first (p-modify db assoc (where (atom db) :name "foo") '(:code "mynewcode"))) :code)
           "bar-code")
          "after trying to update a record that doesn't exist, the old value should still be there.")

      (is (=
           (get (first (p-modify db dissoc (where (atom db) :name "bar") '(:code))) :code)
           nil)
          "after deleting the :code key in the first record, :code should not exist anymore.")

      (is (=
           (get (first (p-modify db dissoc (where (atom db) :name "bar") '(:code2))) :code)
           "bar-code")
          "after trying to delete an unexisting key in the first record, :code should still be there.")

      (is (=
           (get (first (p-rename db (where (atom db) :name "bar") '(:code :password))) :code)
          nil)
          "after renaming a key in the first record, :code should not exist anymore.")

      (is (=
           (get (first (p-rename db (where (atom db) :name "bar") '(:code :password))) :password)
           "bar-code")
          "after renaming a key in the first record, :code should now be named :password.")

      (is (=
           (get (first (p-rename db (where (atom db) :name "bar") '(:test :fail))) :fail)
           nil)
          "after trying to rename a key that doesn't exist in the first record, the renamed key should not exist."))


    (deftest delete-record
      (is (=
           (count (p-delete db (where (atom db) :name "bar")))
           0)
          "after deleting the record, there should be zero record in the database.")
      (is (=
           (count (p-delete db (where (atom db) :name "foo")))
           1)
          "after trying to delete a record that doesn't exist, there should still be one record in the database."))))

