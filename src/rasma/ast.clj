(ns rasma.ast)

(defrecord Span
           [start-offset
            end-offset
            start-line
            start-column
            end-line
            end-column])

(defrecord Node
           [kind
            value
            span])

(defn make-span
  [start-offset end-offset
   start-line start-column
   end-line end-column]
  (->Span start-offset
          end-offset
          start-line start-column
          end-line end-column))

(defn make-node
  [kind value span]
  (->Node kind value span))
