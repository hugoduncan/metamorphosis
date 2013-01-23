(ns meta.morphosis-test
  (:require
   [clojure.test :refer :all]
   [clojure.walk :refer [macroexpand-all]]
   [meta.morphosis :refer [defmprotocol defmtype]]))

;;; A contrived example using arithmetic
(defmprotocol arith
  (add [_ & args] "Add all arguments")
  (mul [_ & args] "Multiply all arguments"))

;;; An implementation that writes expressions to evaluate the arithmetic
(defmtype arith-exec arith
  (:default [arg] (flush) arg)
  (add [op & args] `(+ ~@(mapv #(do `(arith ~%)) args)))
  (mul [op & args] `(* ~@(mapv #(do `(arith ~%)) args))))

;;; An implementation that produces a data-structure describing the arithmetic
(defmtype arith-data arith
  (:default [arg] (if (symbol? arg) (list 'quote arg) arg))
  (add [op & args] `{:op '+ :args ~(mapv #(do `(arith ~%)) args)})
  (mul [op & args] `{:op '* :args ~(mapv #(do `(arith ~%)) args)}))

(in-ns 'meta.morphosis-test)

(deftest arith-exec-test
  (testing "arith-exec"
    (testing "evaluation"
      (is (= 1 (arith-exec 1)))
      (is (= 3 (arith-exec (add 1 2))))
      (is (= 2 (arith-exec (mul 1 2))))
      (is (= 7 (arith-exec (add 1 (mul 2 3))))))))


;; These fail when tun with lein test
;; https://github.com/technomancy/leiningen/issues/912
(deftest ^:not-lein arith-exec-expansion-test
  (testing "arith-exec"
    (testing "expansion"
      (is (= 1 (macroexpand-all `(arith-exec 1))))
      (is (= `(+ 1 2) (macroexpand-all `(arith-exec (add 1 2)))))
      (is (= `(* 1 2) (macroexpand-all `(arith-exec (mul 1 2)))))
      (is (= `(+ 1 (* 2 3))
             (macroexpand-all `(arith-exec (add 1 (mul 2 3)))))))))

(deftest arith-data-test
  (testing "arith-data"
    (testing "evaluation"
      (is (= 1 (arith-data 1)))
      (is (= {:args [1 2], :op 'clojure.core/+}
             (arith-data (add 1 2))))
      (is (= {:args [1 2], :op 'clojure.core/*}
             (arith-data (mul 1 2))))
      (is (= {:args [1 {:args [2 3], :op 'clojure.core/*}]
              :op 'clojure.core/+}
             (arith-data (add 1 (mul 2 3))))))))
