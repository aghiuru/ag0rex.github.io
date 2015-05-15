(ns ^:figwheel-always romtoff.core
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [cljs.core.async :as async :refer [put! chan alts!]]
              [om.core :as om :include-macros true]
              [om-tools.dom :as dom :include-macros true]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(def app-state (atom {:tick 0
                          :entities []}))

(defonce game-chan (chan))

(defn linear [i t p d]
  (let [s (/ p d)]
    (+ i (* (- t i) s))))

(defn cubic-out [i t p d]
  (let [s (- (/ p d) 1)]
    (+ i (* (- t i) (+ 1 (* s s s))))))

(defn bounce-out [i t p d]
  (let [c (- t i)
        s (/ p d)]
    (if (< s (/ 1 2.75))
      (+ i (* c s s 7.5625))
      (if (< s (/ 2 2.75))
        (let [s (- s (/ 1.5 2.75))]
          (+ i (* c (+ 0.75 (* s s 7.5625)))))
        (if (< s (/ 2.5 2.75))
          (let [s (- s (/ 2.25 2.75))]
            (+ i (* c (+ 0.9375 (* s s 7.5625)))))
          (let [s (- s (/ 2.625 2.75))]
            (+ i (* c (+ 0.984375 (* s s 7.5625))))))))))

(defn music-on []
  (.setMusicOnOff (.getElementById js/document "music") true))

(defn music-off []
  (.setMusicOnOff (.getElementById js/document "music") false))

(defn sound-on []
  (.setSoundOnOff (.getElementById js/document "music") true))

(defn sound-off []
  (.setSoundOnOff (.getElementById js/document "music") false))

(defn play-sound [s]
  (.playSound (.getElementById js/document "music") s))

(defn by-id [entity-id]
  (first (filter #(= entity-id (:id %)) (:entities @app-state))))

(defn tell [entity-id message]
  (when-let [entity (by-id entity-id)]
    (let [ch (:ch entity)]
      (put! ch message))))

(def tetriminos [[[1]]

                 [[1 1]]

                 [[1 1 1]]

                 [[1 1]
                  [1 0]]

                 [[1 1]
                  [0 1]]

                 [[1 1 0]
                  [0 1 1]]

                 [[1 1]
                  [1 1]]

                 [[1 1 1 1]]

                 [[1 1 1]
                  [0 1 0]]
                 ])

(def ROWS 13)
(def COLS 9)

(defn in-bounds [[r c]]
  (and (< r ROWS) (< c COLS)))

(defn covered [[r c]]
  (= 1 (get-in @app-state [:clouds r c])))

(defn tetrimino-coords [t [x y]]
  (let [all-tiles (for [r (range (count t))
                        c (range (count (first t)))] [r c])]
    ;; (println all-tiles)
    (reduce (fn [acc [r c]]
              ;; (println r c (get-in tetrimino [r c]))
              (if (= 1 (get-in t [r c]))
                (conj acc [(+ x r) (+ y c)])
                acc))
            [] all-tiles)))

(defn block-id [r c]
  (keyword (str "block-" r "-" c)))

(defn block-coords [block-id]
  (vec (map js/parseInt (subvec (clojure.string/split (name block-id) "-") 1))))

(defn block-by-coords [[x y]]
  (by-id (block-id x y)))

(defn block-ids-by-tetrimino-and-block-id [tetrimino id]
  (map (comp :id block-by-coords) (tetrimino-coords tetrimino (block-coords id))))

(defn get-uncovered-coords []
  (reduce (fn [acc coords]
            (if-not (covered coords)
              (conj acc coords)
              acc))
          []
          (for [r (range ROWS)
                c (range COLS)] [r c])))

(defn is-near [[r1 c1] [r2 c2]]
  (or (and (= c1 c2) (= r1 (dec r2)))
      (and (= c1 c2) (= r1 (inc r2)))
      (and (= r1 r2) (= c1 (dec c2)))
      (and (= r1 r2) (= c1 (inc c2)))))

(defn build-sprite [{:keys [id x y rotation ch animation sprite height width] :as data} owner event-handlers message-handlers]
  (reify
    om/IWillMount
    (will-mount [_]
      (go (loop []
            (let [messages (<! ch)
                  handlers (merge {:tween (fn [content] (om/transact! data :tweens #(merge % content)))
                                   :update (fn [content] (om/transact! data #(merge % content)))
                                   :transact (fn [content] (doseq [[key fn] content] (om/transact! data key fn)))}
                                  message-handlers)]
              (doseq [[type content] messages]
                (let [handler (handlers type)]
                  (if handler
                    (handler content)
                    (.warn js/console (str id ": Missing message handler for " type))))))
            (recur))))
    om/IRender
    (render [_]
      (let [img (if animation (:current animation) sprite)]
        (dom/g (merge {:dangerouslySetInnerHTML #js {:__html (str
                                                              "<image width=\"" width
                                                              "\" height=\"" height
                                                              "\" x=\"" x
                                                              "\" y=\"" y
                                                              "\" xlink:href=\"" img "\" />")}
                       :width width
                       :height height
                       :transform (str "rotate(" (if rotation rotation 0) " " (+ (/ width 2) x) " " (+ (/ height 2) y) ")")}
                      event-handlers))))))

;; (defn block [{:keys [id x y rotation ch animation sprite height width] :as data} owner]
;;   (build-sprite data owner
;;                 {:onMouseOut (fn [_]
;;                                (let [tetrimino (get @app-state :next-tetrimino)]
;;                                  (doseq [affected-block-id (block-ids-by-tetrimino-and-block-id tetrimino id)]
;;                                    (tell affected-block-id {:update {:sprite "img/block.jpg"}})))
;;                                (println "Out" id)
;;                                ;; (tell id {:update {:sprite "img/block.jpg"}})
;;                                "")
;;                  :onMouseOver (fn [_]
;;                                 (let [tetrimino (get @app-state :next-tetrimino)
;;                                       tetrimino-blocks-coords (tetrimino-coords tetrimino (block-coords id))]
;;                                   (when (and (every? #(in-bounds %) tetrimino-blocks-coords)
;;                                              (every? #(covered %) tetrimino-blocks-coords)
;;                                              (some true? (for [uncovered-block-coords (get-uncovered-coords)
;;                                                                block-coords tetrimino-blocks-coords]
;;                                                            (is-near uncovered-block-coords block-coords))))

;;                                     (println tetrimino-blocks-coords)

;;                                     (doseq [affected-block-id (block-ids-by-tetrimino-and-block-id tetrimino id)]
;;                                       (tell affected-block-id {:update {:sprite "img/block-over.jpg"}}))))
;;                                 "")
;;                  :onClick (fn [_]
;;                             (let [tetrimino (get @app-state :next-tetrimino)
;;                                   tetrimino-blocks-coords (tetrimino-coords tetrimino (block-coords id))]
;;                               (when (and (every? #(in-bounds %) tetrimino-blocks-coords)
;;                                          (every? #(covered %) tetrimino-blocks-coords)
;;                                          (some true? (for [uncovered-block-coords (get-uncovered-coords)
;;                                                            block-coords tetrimino-blocks-coords]
;;                                                        (is-near uncovered-block-coords block-coords))))
;;                                 (doseq [affected-block-id (block-ids-by-tetrimino-and-block-id tetrimino id)]
;;                                   (tell affected-block-id {:tween {:x {:target 550
;;                                                                        :duration 10
;;                                                                        :easing :cubic-out}
;;                                                                    :y {:target 1000
;;                                                                        :duration 10
;;                                                                        :easing :cubic-out}}}))
;;                                 (doseq [coords (tetrimino-coords tetrimino (block-coords id))]
;;                                   (println coords)
;;                                   (put! game-chan {:zero-block {:coords coords}})))
;;                               (put! game-chan {:gen-next-tetrimino {}}))
;;                             "")}
;;                 {:boo (fn [_] (put! game-chan {:boo {}}))}))

(defn land [{:keys [id x y rotation ch animation sprite height width stage] :as data} owner]
  (build-sprite data owner
                {:onClick (fn [_] (println id))}
                {:next-state (fn [_]
                               (println "land" id stage)
                               (let [stage (om/get-props owner :stage)]
                                 (case stage
                                   0 (do (tell id {:update {:sprite "img/block-1.jpg" :stage 1}})
                                         (play-sound "crateLand"))
                                   1 (do (tell id {:update {:sprite "img/block-2.jpg" :stage 2}})
                                         (play-sound "crateDrop"))
                                   2 (do (tell id {:update {:sprite "img/block.jpg" :stage 0}})
                                         (play-sound "rockDestroy"))))
                               )}))

(defn water [{:keys [id x y rotation ch animation sprite height width] :as data} owner]
  (build-sprite data owner
                {:onClick (fn [_] (println id))}
                {:next-state (fn [_]
                               (println "water" id)
                               (play-sound "rockDrop"))}))


(defn arrow [{:keys [id x y rotation ch animation sprite height width] :as data} owner]
  (build-sprite data owner
                {:onClick (fn [_]
                            (put! game-chan {:clear-selection {}})

                            (let
                                [t (get @app-state :next-tetrimino)
                                 c (block-coords id)
                                 tetrimino-blocks-coords (tetrimino-coords t c)]
                              (doseq [block-coords tetrimino-blocks-coords]
                                (tell (apply block-id block-coords) {:next-state {}}))
                              (println tetrimino-blocks-coords))

                            (put! game-chan {:gen-next-tetrimino {}})
                            (println id)
                            )}
                {}))

(defn falling-circle [{:keys [ch x y] :as data} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go (loop []
            (let [messages (<! ch)]
              (doseq [[type content] messages]
                (case type
                  :tween (om/transact! data :tweens #(merge % content))
                  :update (om/transact! data #(merge % content))
                  :transact (doseq [[key fn] content]
                              (om/transact! data key fn)))))
            (recur))))
    om/IRender
    (render [_]
      (dom/circle #js {:cx x :cy y :r 25
                       :onClick (fn [_] (put! game-chan :create) "")}))))

(defn from-default-entity [m]
  (merge {:ch (chan) :tweens {}} m))

(defn add-entity [data entity]
  (om/transact! data :entities #(conj % entity)))

(defmulti builder
  (fn [data owner] (:type data)))

(defmethod builder :sprite [data owner] (build-sprite data owner {} {}))

;; (defmethod builder :block [data owner] (block data owner))

(defmethod builder :land [data owner] (land data owner))

(defmethod builder :water [data owner] (water data owner))

(defmethod builder :arrow [data owner] (arrow data owner))

(defmethod builder :falling-circle [data owner] (falling-circle data owner))

(defn ones [r c]
  (let [row (vec (map (constantly 1) (range r)))
        m (vec (map (constantly row) (range c)))]
    m))

(def level-1
  [[0 0 0 0 0 0 0 0 0]
   [0 0 0 0 0 0 0 0 0]
   [0 0 0 1 0 0 0 0 0]
   [0 0 1 1 1 1 1 0 0]
   [0 0 1 1 0 1 1 0 0]
   [0 0 1 1 1 1 1 0 0]
   [0 0 1 0 1 0 1 0 0]
   [0 0 1 1 1 1 1 0 0]
   [0 0 1 1 1 0 1 0 0]
   [0 0 1 1 1 1 1 1 0]
   [0 0 0 0 0 0 0 0 0]
   [0 0 0 0 0 0 0 0 0]
   [0 0 0 0 0 0 0 0 1]])

(def level-2
  [[0 0 0 0 1 0 0 0 0]
   [0 0 0 1 0 1 0 0 0]
   [0 0 1 1 0 1 1 0 0]
   [0 0 0 1 0 1 0 0 0]
   [0 0 0 0 1 0 0 0 0]
   [0 0 0 0 1 0 0 0 0]
   [1 1 0 1 1 1 0 1 1]
   [1 0 0 1 1 1 0 0 1]
   [1 1 0 1 1 1 0 1 1]
   [1 1 0 1 1 1 0 1 1]
   [0 1 1 1 1 1 1 1 0]
   [0 0 1 1 1 1 1 0 0]
   [0 0 0 0 1 0 0 0 0]])

(defn is-land [r c level]
  (= 1 (get-in level [r c])))

(def X-OFFSET 8)
(def Y-OFFSET 8)
(def TILE-WIDTH 70)
(def TILE-HEIGHT 70)

(om/root
  (fn [data owner]
    (reify
      om/IInitState
      (init-state [_]
        {:game-chan game-chan})

      om/IWillMount
      (will-mount [_]
        (js/setInterval #(om/transact! data :tick inc) 34)


        (add-entity data (from-default-entity {:id :map
                                               :type :sprite
                                               :height 910
                                               :width 630
                                               :x 0
                                               :y 0
                                               :sprite "img/map_1.png"}))
        (let [level level-2]
          (doseq [r (range (count level))
                  c (range (count (first level)))]
            (if (is-land r c level)
              (add-entity data (from-default-entity {:id (block-id r c)
                                                     :type :land
                                                     :x (* c 70)
                                                     :y (* r 70)
                                                     :height 70
                                                     :width 70
                                                     :sprite "img/block.jpg"
                                                     :stage 0}))

              (add-entity data (from-default-entity {:id (block-id r c)
                                                     :type :water
                                                     :x (* c 70)
                                                     :y (* r 70)
                                                     :height 70
                                                     :width 70
                                                     :sprite "img/block-water.jpg"})))))

        ;; (doseq [r (range ROWS)
        ;;         c (range COLS)]
        ;;   (let [id (block-id r c)]
        ;;     (add-entity data (from-default-entity {:id id
        ;;                                            :type :block
        ;;                                            :x (* c 70)
        ;;                                            :y (* r 70)
        ;;                                            :height 70
        ;;                                            :width 70
        ;;                                            :sprite "img/block.jpg"}))))

        ;; (om/update! data :clouds (ones COLS ROWS))
        ;; (om/transact! data :clouds #(assoc-in % [12 0] 0))

        (add-entity data (from-default-entity {:id :circle-1
                                               :type :falling-circle}))

        (let [game-chan (om/get-state owner :game-chan)]
          ;; Game channel.
          (let [handler (fn [messages] (doseq [[type contents] messages]
                                         (println type)
                                         (case type
                                           :selection
                                           (do
                                               (let [[r c] (:current contents)]
                                                 (put! game-chan {:clear-selection {}})
                                                 (put! game-chan {:tetrimino-at {:coords [r c]}})))

                                           :clear-selection
                                           (do
                                             (let [data (om/get-props owner)
                                                   entities (:entities data)
                                                   arrow-ids (set (map :id (filter #(= :arrow (:type %)) entities)))
                                                   no-arrows (vec (remove #(contains? arrow-ids (:id %)) entities))]
                                               (om/update! data :entities no-arrows)))

                                           :tetrimino-at
                                           (do
                                               (let [t (get @app-state :next-tetrimino)
                                                     c (:coords contents)]
                                                 (println "message" t c)
                                                 (let [tetrimino-blocks-coords (tetrimino-coords t c)]
                                                   ;; Selection must be inside map.
                                                   (when (every? #(in-bounds %) tetrimino-blocks-coords)

                                                     (doseq [[r c :as tbc] tetrimino-blocks-coords]
                                                       (add-entity data (from-default-entity {:id (keyword (str "arrow-" r "-" c))
                                                                                              :type :arrow
                                                                                              :x (* c 70)
                                                                                              :y (* r 70)
                                                                                              :height 70
                                                                                              :width 70
                                                                                              :sprite "img/sageata.png"}))))
                                                   (println tetrimino-blocks-coords))))

                                           :gen-next-tetrimino (om/update! data :next-tetrimino (rand-nth tetriminos))
                                           ;; :message action
                                           (.warn js/console (str "Game: Missing message handler for " type)))))]
            (go (loop []
                  (let [messages (<! game-chan)]
                    (handler messages))
                  (recur))))))

      om/IDidMount
      (did-mount [_]
        (tell :block-0-0 {:boo true})
        (tell :circle-1 {:update {:x (rand 600)}
                         :tween {:y {:target 1000
                                     :duration 30
                                     :easing :bounce-out
                                     }
                                 :x {:target (rand 600)
                                     :duration 60
                                     :easing :cubic-out
                                     :when-done :new-ball}}})

        (om/update! data :next-tetrimino (rand-nth tetriminos))

        (js/setTimeout (fn [_]
                         (music-off)
                         (music-on)
                         (sound-off)
                         (sound-on))
                       1000))

      om/IRenderState
      (render-state [_ {:keys [game-chan]}]

        ;; Tween system.
        (doseq [entity (get data :entities)]
               (doseq [[key {:keys [target duration easing progress initial when-done] :as tween}] (get entity :tweens)]
                 (if-not progress
                   (do
                     (om/update! tween :progress 0)
                     (om/update! tween :initial (get entity key)))
                   (do
                     (let [easing-fn (case easing :linear linear :cubic-out cubic-out :bounce-out bounce-out)]
                       (om/update! entity key (easing-fn initial target progress duration)))
                     (om/transact! tween :progress inc)
                     (when (= duration progress)
                       (om/transact! entity :tweens #(dissoc % key))
                       (when when-done (put! game-chan {when-done {}})))))))

        ;; Animation system.
        (doseq [entity (get data :entities)]
          (when-let [{:keys [frames duration progress current] :as animation} (:animation entity)]
            (if-not progress
              (do
                (om/update! animation :progress 0)
                (om/update! animation :current (first frames)))
              (do
                (om/transact! animation :progress inc)
                (when (= duration progress)
                  (om/update! animation :progress 0)
                  (let [current-index (.indexOf (to-array frames) current)
                        next-index (if (= (dec (count frames)) current-index) 0 (inc current-index))]
                    (om/update! animation :current (get frames next-index))))))))

        (dom/div nil
                 (dom/svg #js {:width 640
                               :height 1136
                               :style #js {:float "left"
                                           :border "1px solid lightgray"}
                               :onMouseMove (fn [e]
                                              (let [x (.-pageX e)
                                                    y (.-pageY e)]
                                                (om/update! data [:mouse :prev] (get-in data [:mouse :current]))
                                                (om/update! data [:mouse :current] {:x x :y y})

                                                ;; Selection.
                                                (let [new-selection [(quot (- y Y-OFFSET) TILE-HEIGHT)
                                                                     (quot (- x X-OFFSET) TILE-WIDTH)]
                                                      current-selection (get-in data [:selection :current])]
                                                  (when (not= new-selection current-selection)
                                                    (om/update! data [:selection :prev] current-selection)
                                                    (om/update! data [:selection :current] new-selection)
                                                    (put! game-chan {:selection {:prev current-selection
                                                                                 :current new-selection}})))

                                                (when (get-in data [:mouse :down])
                                                  (let [{:keys [current prev]} (get data :mouse)
                                                        dx (- (current :x) (prev :x))
                                                        dy (- (current :y) (prev :y))]
                                                    (do
                                                      ;; Drag.
                                                      )))))

                               :onMouseDown (fn [e]
                                              (om/update! data [:mouse :down] {:x (.-pageX e) :y (.-pageY e)}))

                               :onMouseUp (fn [e]
                                            (om/update! data [:mouse :down] false))}

                          (dom/rect #js {:x 0 :y 0
                                         :width 640 :height 1136
                                         :style #js {:fill "rgb(250, 250, 200)"}})

                          (dom/g nil
                                 (map (fn [{:keys [type] :as entity}]
                                        (om/build builder entity {:init-state {:game-chan game-chan}}))
                                      (get data :entities))))

                 ;; Inspector.
                 ;; (dom/div #js {:style #js {:float "left"
;;                                            :width 400
;;                                            :height 800}}
;; ;;                          (prn-str data)

;;                           (let [data @data
;;                                 no-chan-entities (reduce #(conj %1 (dissoc %2 :ch)) [] (:entities data))
;;                                 no-chan-map (merge data {:entities no-chan-entities})]
;;                             (dom/pre nil
;;                                      (.stringify js/JSON (clj->js (:entities no-chan-map)) nil 4))))
                 ))))
  app-state
  {:target (. js/document (getElementById "app"))})

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)