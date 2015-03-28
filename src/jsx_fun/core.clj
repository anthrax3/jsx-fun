(ns jsx-fun.core
  (:require [clojure.java.io :as io]
            [cljs.closure :as cl])
  (:import [com.google.common.base Predicates]
           [javax.script ScriptEngineManager]
           [com.google.javascript.jscomp
            ES6ModuleLoader ProcessCommonJSModules CompilerOptions SourceFile
            CompilerOptions$LanguageMode NodeUtil NodeUtil$Visitor]
           [com.google.javascript.jscomp.parsing
            Config Config$LanguageMode ParserRunner]
           [com.google.javascript.rhino
            Node Token ErrorReporter SimpleErrorReporter]))

(defn set-options [opts ^CompilerOptions compiler-options]
  (doseq [[k v] opts]
    (condp = k
      :lang-in
      (case v
        :es5 (.setLanguageIn compiler-options
               CompilerOptions$LanguageMode/ECMASCRIPT5))
      :pretty-print
      (set! (.prettyPrint compiler-options) v)))
  compiler-options)

(defn jsx-engine []
  (let [nashorn (.getEngineByName (ScriptEngineManager.) "nashorn")]
    (doto nashorn
      (.eval "var global = {};")
      (.eval (io/reader (io/resource "com/facebook/jsx.js"))))))

(defn transform-jsx
  ([src] (transform-jsx (jsx-engine) src))
  ([jsx-eng src]
   (let [options
         (.eval jsx-eng
           "(function(){ return {stripTypes: true, harmony: true};})()")
         jsxt (.eval jsx-eng "global.JSXTransformer")]
     (.get
       (.invokeMethod jsx-eng jsxt "transform"
         (object-array [src options]))
       "code"))))

(defn transform-commonjs
  [filename src]
  (let [js      [(SourceFile/fromCode filename src)]
        options (set-options {:lang-in :es5 :pretty-print true}
                  (CompilerOptions.))
        comp    (doto (cl/make-closure-compiler)
                  (.init '() js options))
        root    (.parse comp (first js))]
    (.process
      (ProcessCommonJSModules. comp
        (ES6ModuleLoader. comp "./")
        false)
      nil root)
    (.toSource comp root)))

(defn transform-commonjs
  [filename src]
  (let [js      [(SourceFile/fromCode filename src)]
        options (set-options {:lang-in :es5 :pretty-print true}
                  (CompilerOptions.))
        comp    (doto (cl/make-closure-compiler)
                  (.init '() js options))
        root    (.parse comp (first js))]
    (.getJSDoc root)))

(defn node-visitor []
  (reify
    NodeUtil$Visitor
    (visit [_ node]
      (println (Token/name (.getType node)))
      (when-let [js-doc (.getJSDocInfo node)]
        (println js-doc)))))

;(defn transform-commonjs
;  [filename src]
;  (let [^List externs '()
;        ^List inputs [(SourceFile/fromCode filename src)]
;        ^CompilerOptions options (set-options {:lang-in :es5 :type :commonjs} (CompilerOptions.))
;        compiler (cl/make-closure-compiler)
;        ^Result result (.compile compiler externs inputs options)]
;    (if (.success result)
;      (.toSource compiler)
;      (cl/report-failure result))))

(comment
  (spit
    (io/file "resources/ScrollResponder.out.js")
    (transform-jsx (slurp (io/file "resources/ScrollResponder.js"))))

  ;; the file name is used as the module name
  ;; we will have to extract this from @providesModule
  (println
    (transform-commonjs
      "ScrollResponder.js"
      (transform-jsx (slurp (io/file "resources/ScrollResponder.js")))))

  (let [config       (ParserRunner/createConfig
                       true Config$LanguageMode/ECMASCRIPT5 true nil)
        err-reporter (SimpleErrorReporter.)
        source-file  (SourceFile/fromFile
                       (io/file "resources/ScrollResponder.js"))
        parse-result (ParserRunner/parse
                       source-file (.getCode source-file)
                       config err-reporter)
        visitor      (node-visitor)]
    (println
      (.comments parse-result))
    (NodeUtil/visitPreOrder
      (.ast parse-result) visitor (Predicates/alwaysTrue)))
  )