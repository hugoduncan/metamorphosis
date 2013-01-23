# metamorphosis

Macro protocols for clojure.

## Usage

The library provides a `defmprotocol` form for defining a macro protocol.

```clj
(defmprotocol arith
  (add [_ & args] "Add all arguments")
  (mul [_ & args] "Multiply all arguments"))
```

The protocol can then be implemented using the `defmtype` form.

```clj
(defmtype arith-exec arith
  (:default [arg] (flush) arg)
  (add [op & args] `(+ ~@(mapv #(do `(arith ~%)) args)))
  (mul [op & args] `(* ~@(mapv #(do `(arith ~%)) args))))
```

To use the macro protocol, write your forms within the scope of an
implementation:

```clj
(arith-exec (add 1 (mul 2 3))) => 7
```

[API docs](http://hugoduncan.github.com/metamorphosis/api/0.1)

## Artifacts

Metamorphosis is released to Clojars.

With Leiningen:

```clj
[metamorphosis "0.1.0"]
```

With Maven:

```xml
<dependency>
  <groupId>metamorphosis</groupId>
  <artifactId>metamorphosis</artifactId>
  <version>0.1.0</version>
</dependency>
```

## License

Copyright © 2013 Hugo Duncan

Distributed under the Eclipse Public License.
