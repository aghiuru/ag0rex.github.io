// Compiled by ClojureScript 0.0-3211 {}
goog.provide('fipp.deque');
goog.require('cljs.core');
goog.require('clojure.core.rrb_vector');
fipp.deque.create = cljs.core.vector;
fipp.deque.empty = cljs.core.PersistentVector.EMPTY;
fipp.deque.conjr = cljs.core.fnil.call(null,cljs.core.conj,fipp.deque.empty);
fipp.deque.conjlr = (function fipp$deque$conjlr(l,deque,r){
return clojure.core.rrb_vector.catvec.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [l], null),deque,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [r], null));
});
fipp.deque.concat = clojure.core.rrb_vector.catvec;

//# sourceMappingURL=deque.js.map?rel=1431292024317