(ns meta.morphosis
  "Macro protocols.")

(defn resolved-symbol
  "Resolve symbol, and return a ns-qualified symbol."
  [x]
  {:pre [(symbol? x)]}
  (when-let [v (resolve x)]
    (let [m (meta v)]
      (symbol (-> m :ns ns-name name) (-> m :name name)))))

(defn symbol-for
  "Return a symbol based on the var's name and suffix, using the value provided
  by looking up the given kw in the given var's metadata.  This allows the value
  to be cached in the metadata."
  [v-sym kw suffix]
  (let [v (resolve v-sym)]
    (or (and v (kw (meta v)))
        (gensym (str (name v-sym) suffix)))))

(defmacro defmprotocol
  "Define a macro protocol. Within the scope of any implementation, the
  macros defined in the protocol will be dispatched to the implementation.

      (defmprotocol mp
        ; macro signatures
        (bar [this a b] \"bar docs\")
        (baz [this a] \"baz docs\"))

  Implementations are provided using the `defmtype` macro."
  [pname & fns]
  (let [m (reduce
           (fn [m form]
             (assoc m
               (keyword (name (ns-name *ns*)) (name (first form)))
               {:meta (meta form) :args (list 'quote (second form))}))
           {} fns)
        ;; Put the method fn description on the metadata, so defmtype can check
        ;; against these
        pname (vary-meta pname assoc :fns m)
        ;; Ensure we have a var to set with the current implementation.
        ;; The implementation provides the multi-methods to dispatch on.
        pname (vary-meta
               pname update-in [::impl-var]
               #(or % (.. clojure.lang.Var (create (ThreadLocal.)))))]
    `(do
       ;; Define a var for each function, which will be used to dispatch
       ;; on
       ~@(mapv #(do `(def ~(symbol (name %)))) (keys m))
       ;; Define the protocol function, which when called, will dispatch
       ;; based on the current implementation
       (defmacro ~pname [form#]
         (let [tl# (::impl-var (meta #'~pname))
               impl# (.get @tl#)
               [method# args#] (if (seq? form#) ; handle scalar literals
                                 [(or (resolved-symbol (first form#))
                                      (first form#))
                                  (rest form#)]
                                 [form# nil])]
           (assert
            impl#
            ~(str "Not calling " pname
                  " from within an implementing mtype form"))
           (apply impl# method# args#)))
       ;; Provide a convenience function to set the current implementation
       ;; for testing, etc.
       (defn ~(symbol (str "set-" (name pname) "-impl!")) [impl#]
         (.set @(::impl-var (meta #'~pname)) impl#)))))

(defmacro defmtype
  "Provides an implementation of a macro protocol defined with `defmprotocol`.

       (defmtype name mprotocol
         (bar [op a b] `(do 'some 'code))
         (baz [op a] (list 'quote (symbol \"something-else\"))))

  Apart from the macro functions defined in the protocol, you may
  also provide a `:default` function, that is called on any form that
  doesn't match a macro protocol function.

  A `:dispatch` function can also be provided, that overrides the
  default macro body for the macro type."
  [tname pname & fn-impls]
  (let [pvar (resolve pname)
        _ (assert pvar (str "mprotocol " pname " not found"))
        {:keys [fns ns]} (meta pvar)
        ;; split off any :dispatch function
        dispatch (first (filter #(= :dispatch (first %)) fn-impls))
        fn-impls (remove #(= :dispatch (first %)) fn-impls)
        multi-sym (symbol-for tname ::multi "-multi")
        dispatch-sym (symbol-for tname ::dispatch "-dispatch")
        tname (vary-meta tname assoc
                         ::multi (list 'quote multi-sym)
                         ::dispatch (list 'quote dispatch-sym))
        impl-sym (fn [s]
                   (if (= :default s)
                     s
                     (if-let [v (->> (keys fns)
                                     (filter #(= (name s) (name %)))
                                     first)]
                       (symbol (namespace v) (name v))
                       (assert false
                               (str "Trying to implement method " s
                                    " which isn't in the mprotocol " pname)))))]
    `(do
       ;; Define a multi-method for this mtype's functions
       (defmulti ~multi-sym (fn [f# & args#] f#))

       ;; Define a method for each of the mtype's functions
       ~@(mapv
          (fn [form]
            (let [[s args & body] form]
              (with-meta
                `(defmethod ~multi-sym '~(impl-sym s) ~args ~@body)
                (meta form))))
          fn-impls)

       ;; Define a helper to set the protocol implementation which is useful for
       ;; macro-expansion when debugging.
       (defn ~(symbol (str "use-" (name tname))) []
         (.set @(::impl-var (meta #'~pname)) #'~multi-sym))

       ;; Define a dispatch macro, defaulting to an identity macro.
       ~(if (seq dispatch)
          `(defn ~dispatch-sym ~(second dispatch)
             ~(last dispatch))
          `(defn ~dispatch-sym [args#] (list* '~pname args#)))

       ;; Define the mtype's main macro, which the user will wrap his forms that
       ;; use the mprotocol in.  The form just sets the protocol's
       ;; implementation and allows expansion to continue.
       (defmacro ~tname [& args#]
         (.set @(::impl-var (meta #'~pname)) ~multi-sym)
         (~dispatch-sym args#)))))
