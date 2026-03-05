// game.ts - Wordle Game (Enterprise Edition)
// TODO: add tests
// TODO: refactor this entire file
// TODO: learn what "separation of concerns" means
// TODO: stop writing TODOs and write actual code
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
// ============================================================
// SECTION 1: CONSTANTS (SO MANY CONSTANTS)
// ============================================================
const GAME_CONFIG_MAXIMUM_NUMBER_OF_ATTEMPTS_ALLOWED_PER_GAME_SESSION = 6;
const GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS = 5;
const ANIMATION_FLIP_REVEAL_DURATION_IN_MILLISECONDS = 500;
const ANIMATION_SHAKE_INVALID_WORD_DURATION_IN_MILLISECONDS = 500;
const ANIMATION_TILE_POP_DURATION_IN_MILLISECONDS = 100;
const TOAST_MESSAGE_DEFAULT_DISPLAY_DURATION_MS = 1500;
const TOAST_MESSAGE_FADE_OUT_DURATION_MS = 300;
const TILE_REVEAL_STAGGER_DELAY_MS_PER_COLUMN = 300;
const STATE_DEEP_CLONE_ON_READ_ENABLED_FLAG = true; // disabling this will probably break stuff
const DEV_MODE_LOG_TARGET_WORD_TO_CONSOLE = true; // oops, left this on
const GAME_CONFIG_KEYBOARD_ROWS_KEY_LAYOUT_DEFINITION = [
    ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
    ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
    ['ENTER', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '⌫']
];
// ============================================================
// SECTION 2: ENUMS AND TYPES (WAY TOO MANY)
// ============================================================
// This enum and the LetterEvaluation type below say the same thing. Both are used.
var TileStateEnum;
(function (TileStateEnum) {
    TileStateEnum["EMPTY"] = "empty";
    TileStateEnum["TBD"] = "tbd";
    TileStateEnum["CORRECT"] = "correct";
    TileStateEnum["PRESENT"] = "present";
    TileStateEnum["ABSENT"] = "absent";
})(TileStateEnum || (TileStateEnum = {}));
// Another redundant enum used in only one place
var EvaluationResultCode;
(function (EvaluationResultCode) {
    EvaluationResultCode[EvaluationResultCode["LETTER_IN_CORRECT_POSITION"] = 0] = "LETTER_IN_CORRECT_POSITION";
    EvaluationResultCode[EvaluationResultCode["LETTER_IN_WRONG_POSITION"] = 1] = "LETTER_IN_WRONG_POSITION";
    EvaluationResultCode[EvaluationResultCode["LETTER_NOT_IN_WORD_AT_ALL"] = 2] = "LETTER_NOT_IN_WORD_AT_ALL";
})(EvaluationResultCode || (EvaluationResultCode = {}));
// ============================================================
// SECTION 4: THE GIANT WORD LIST
// ============================================================
const THE_COMPLETE_AND_EXHAUSTIVE_LIST_OF_ALL_VALID_FIVE_LETTER_WORDS = [
    'about', 'above', 'abuse', 'actor', 'acute', 'admit', 'adopt',
    'adult', 'after', 'again', 'agent', 'agree', 'ahead', 'alarm',
    'album', 'alert', 'alike', 'alive', 'alley', 'allow', 'alone',
    'along', 'alter', 'angel', 'angle', 'angry', 'ankle', 'apart',
    'apple', 'apply', 'arena', 'argue', 'arise', 'array', 'arrow',
    'aside', 'attic', 'audio', 'awake', 'aware', 'awful', 'basic',
    'basis', 'beach', 'beard', 'began', 'begin', 'being', 'below',
    'bench', 'black', 'blade', 'blame', 'bland', 'blank', 'blast',
    'blaze', 'bleed', 'blend', 'bless', 'blind', 'block', 'blood',
    'bloom', 'blown', 'blues', 'blunt', 'board', 'bonus', 'boost',
    'booth', 'bound', 'boxer', 'brain', 'brand', 'brave', 'break',
    'breed', 'brick', 'bride', 'brief', 'bring', 'broad', 'broke',
    'brook', 'brown', 'brush', 'budge', 'build', 'built', 'burst',
    'buyer', 'cabin', 'candy', 'carry', 'catch', 'cause', 'chain',
    'chair', 'chalk', 'chaos', 'charm', 'chase', 'cheap', 'check',
    'chess', 'chest', 'chief', 'child', 'chill', 'choir', 'chunk',
    'civic', 'civil', 'claim', 'clash', 'clasp', 'class', 'clean',
    'clear', 'clerk', 'click', 'cliff', 'climb', 'cling', 'clock',
    'clone', 'close', 'cloth', 'cloud', 'coach', 'coast', 'comma',
    'comet', 'coral', 'could', 'count', 'court', 'cover', 'craft',
    'crane', 'crash', 'crazy', 'creek', 'crime', 'crisp', 'cross',
    'crowd', 'crown', 'cruel', 'crumb', 'crush', 'curve', 'cycle',
    'daddy', 'daily', 'dance', 'datum', 'dealt', 'death', 'debut',
    'delay', 'dense', 'depot', 'depth', 'derby', 'devil', 'dirty',
    'disco', 'ditch', 'diver', 'dizzy', 'dodge', 'dogma', 'doing',
    'donor', 'doubt', 'dough', 'draft', 'drain', 'drama', 'drank',
    'drawn', 'dream', 'dress', 'dried', 'drink', 'drive', 'drone',
    'drove', 'drums', 'dryer', 'dying', 'eager', 'early', 'earth',
    'eight', 'elite', 'empty', 'ended', 'enemy', 'enjoy', 'enter',
    'entry', 'equal', 'error', 'event', 'every', 'exact', 'exist',
    'extra', 'fable', 'faith', 'false', 'fancy', 'fatal', 'fault',
    'feast', 'fence', 'fiber', 'field', 'fifth', 'fight', 'final',
    'first', 'fixed', 'flame', 'flash', 'fleet', 'flesh', 'float',
    'flood', 'floor', 'flour', 'fluid', 'flush', 'focus', 'force',
    'forge', 'forth', 'forum', 'found', 'frame', 'frank', 'fraud',
    'fresh', 'front', 'frost', 'froze', 'fruit', 'funny', 'ghost',
    'giant', 'given', 'glass', 'globe', 'gloom', 'glory', 'gloss',
    'glove', 'going', 'grace', 'grade', 'grain', 'grand', 'grant',
    'grasp', 'grass', 'grave', 'gravy', 'graze', 'great', 'green',
    'greet', 'grief', 'grill', 'grind', 'groan', 'gross', 'group',
    'grove', 'grown', 'guard', 'guile', 'guilt', 'guise', 'gusto',
    'happy', 'harsh', 'haste', 'haven', 'heart', 'heavy', 'herbs',
    'hinge', 'hoist', 'holly', 'honey', 'honor', 'horse', 'hotel',
    'hound', 'house', 'human', 'humor', 'hurry', 'hyper', 'ideal',
    'image', 'imply', 'index', 'infer', 'inner', 'input', 'irony',
    'issue', 'ivory', 'japan', 'jelly', 'jewel', 'joker', 'judge',
    'juice', 'juicy', 'jumbo', 'knack', 'kneel', 'knife', 'knock',
    'known', 'lapse', 'laser', 'laugh', 'layer', 'learn', 'lease',
    'leash', 'least', 'leave', 'legal', 'lemon', 'level', 'light',
    'limit', 'linen', 'liner', 'liver', 'local', 'lodge', 'logic',
    'loose', 'lover', 'lower', 'lucky', 'lunch', 'lying', 'magic',
    'major', 'maker', 'manor', 'maple', 'march', 'marks', 'match',
    'mayor', 'media', 'mercy', 'merit', 'metal', 'might', 'minor',
    'minus', 'mixer', 'model', 'money', 'month', 'moral', 'motor',
    'mourn', 'mouth', 'movie', 'muddy', 'music', 'nasal', 'naval',
    'nerve', 'never', 'newly', 'night', 'ninja', 'noise', 'north',
    'noted', 'novel', 'nurse', 'nymph', 'occur', 'ocean', 'offer',
    'often', 'olive', 'onset', 'opera', 'order', 'ought', 'outer',
    'oxide', 'ozone', 'paint', 'panel', 'panic', 'paper', 'party',
    'pasta', 'patch', 'pause', 'peace', 'peach', 'pearl', 'pedal',
    'penny', 'perch', 'petty', 'phase', 'phone', 'photo', 'piano',
    'piece', 'pilot', 'pinch', 'pixel', 'pizza', 'place', 'plain',
    'plane', 'plant', 'plate', 'plaza', 'plead', 'pluck', 'plumb',
    'plume', 'plump', 'point', 'polar', 'polka', 'porch', 'power',
    'press', 'price', 'pride', 'prime', 'print', 'prior', 'prize',
    'probe', 'prone', 'proof', 'proud', 'prune', 'psalm', 'pulse',
    'punch', 'pupil', 'purse', 'queen', 'query', 'quest', 'queue',
    'quick', 'quiet', 'quota', 'quote', 'radar', 'radio', 'rainy',
    'raise', 'range', 'rapid', 'ratio', 'reach', 'ready', 'realm',
    'rebel', 'refer', 'reign', 'relax', 'reply', 'rider', 'ridge',
    'rifle', 'right', 'risky', 'rival', 'river', 'robot', 'rocky',
    'rouge', 'rough', 'round', 'route', 'royal', 'rugby', 'ruler',
    'rural', 'sadly', 'saint', 'salad', 'sauce', 'scale', 'scary',
    'scene', 'scope', 'score', 'scout', 'sense', 'setup', 'seven',
    'shade', 'shake', 'shall', 'shame', 'shape', 'share', 'shark',
    'sharp', 'sheet', 'shelf', 'shell', 'shift', 'shine', 'shirt',
    'shock', 'shoot', 'short', 'shout', 'shove', 'shown', 'sight',
    'silly', 'since', 'sixth', 'sixty', 'skill', 'skull', 'skunk',
    'slant', 'slash', 'slave', 'sleep', 'sleet', 'slept', 'slide',
    'slime', 'sloth', 'slump', 'small', 'smart', 'smell', 'smile',
    'smoke', 'snack', 'snake', 'sneak', 'solid', 'solve', 'sorry',
    'south', 'space', 'spare', 'spark', 'spawn', 'speak', 'spear',
    'speed', 'spend', 'spice', 'spike', 'spine', 'spite', 'split',
    'spoke', 'spook', 'spoon', 'sport', 'spray', 'squad', 'staff',
    'stage', 'stain', 'stair', 'stake', 'stale', 'stalk', 'stall',
    'stamp', 'stand', 'stark', 'start', 'state', 'stays', 'steak',
    'steal', 'steam', 'steel', 'steep', 'steer', 'stern', 'stick',
    'stiff', 'still', 'stomp', 'stone', 'stood', 'stool', 'store',
    'storm', 'story', 'stove', 'strap', 'straw', 'stray', 'strip',
    'stuck', 'study', 'stuff', 'style', 'sugar', 'suite', 'sunny',
    'super', 'surge', 'swamp', 'swear', 'sweet', 'swept', 'swift',
    'swing', 'sword', 'swore', 'sworn', 'syrup', 'table', 'taken',
    'taste', 'teach', 'tears', 'tense', 'tenth', 'terms', 'terse',
    'thank', 'theme', 'there', 'these', 'thick', 'thing', 'think',
    'third', 'those', 'three', 'threw', 'throw', 'thumb', 'tiger',
    'timed', 'timer', 'tired', 'title', 'today', 'token', 'totem',
    'touch', 'tough', 'tower', 'toxic', 'trace', 'track', 'trade',
    'trail', 'train', 'trait', 'tramp', 'trash', 'trawl', 'treat',
    'trend', 'tribe', 'trick', 'tried', 'troop', 'trout', 'truck',
    'truly', 'tryst', 'tumor', 'tuner', 'tunic', 'twice', 'twist',
    'typed', 'ulcer', 'ultra', 'under', 'union', 'unity', 'until',
    'upper', 'upset', 'urban', 'usage', 'usher', 'usual', 'utter',
    'valid', 'value', 'valve', 'vapor', 'vault', 'video', 'vigor',
    'vinyl', 'viral', 'virus', 'visit', 'visor', 'vista', 'vital',
    'vivid', 'voice', 'voter', 'waltz', 'waste', 'watch', 'water',
    'weary', 'weave', 'wedge', 'weird', 'wheat', 'wheel', 'where',
    'which', 'while', 'white', 'whole', 'wider', 'widow', 'wield',
    'witch', 'woman', 'world', 'worry', 'worst', 'worth', 'would',
    'wound', 'wrath', 'write', 'wrong', 'yacht', 'yield', 'young',
    'youth', 'zebra', 'zones', 'zoned', 'zippy', 'zappy'
];
// ============================================================
// SECTION 5: UTILITY FUNCTIONS (UNNECESSARY WRAPPERS)
// ============================================================
// generates a UUID that is never actually needed
function generateCompletelyUnnecessaryPseudoRandomUUIDv4String() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (charPlaceholder) {
        const randomHexDigit = Math.random() * 16 | 0;
        const finalHexValue = charPlaceholder === 'x' ? randomHexDigit : (randomHexDigit & 0x3 | 0x8);
        return finalHexValue.toString(16);
    });
}
// wraps JSON.parse(JSON.stringify()) because apparently that deserves its own function
function performDeepCloneOperationOnArbitraryObject(objectToBeCloned) {
    if (objectToBeCloned === null || typeof objectToBeCloned !== 'object') {
        return objectToBeCloned;
    }
    return JSON.parse(JSON.stringify(objectToBeCloned));
}
// wrapper around Math.random() * n | 0 for some reason
function getRandomIntegerBetweenZeroInclusiveAndMaxExclusive(maxValueExclusive) {
    return Math.floor(Math.random() * maxValueExclusive);
}
// wrapper around the wrapper
function selectOneRandomElementFromTheProvidedArray(sourceArray) {
    const chosenIndex = getRandomIntegerBetweenZeroInclusiveAndMaxExclusive(sourceArray.length);
    return sourceArray[chosenIndex];
}
// ============================================================
// SECTION 6: WORD EVALUATION ENGINE
// The actual game logic. Overcomplicated but correct.
// ============================================================
class WordEvaluationEngineV2TheFinalVersion {
    constructor() {
        this._internalEvaluationResultCacheMap = new Map();
        this._totalNumberOfEvaluationsPerformedCounter = 0;
    }
    /**
     * Evaluates a guess against the target word.
     * Uses a patented* two-pass algorithm.
     * (*not actually patented)
     */
    evaluateGuessWordAgainstTargetWord(guessWordInput, targetWordInput) {
        const performanceTimerStart = performance.now();
        // Build a cache key so we don't evaluate the same pair twice
        const evaluationCacheKeyString = `${guessWordInput.toUpperCase()}__VERSUS__${targetWordInput.toUpperCase()}`;
        if (this._internalEvaluationResultCacheMap.has(evaluationCacheKeyString)) {
            return this._internalEvaluationResultCacheMap.get(evaluationCacheKeyString);
        }
        const normalizedUpperCaseGuessWord = guessWordInput.toUpperCase();
        const normalizedUpperCaseTargetWord = targetWordInput.toUpperCase();
        // Initialize all results to absent
        const finalEvaluationResultArray = new Array(GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS).fill(TileStateEnum.ABSENT);
        const rawEvaluationCodeResultArray = new Array(GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS).fill(EvaluationResultCode.LETTER_NOT_IN_WORD_AT_ALL);
        // Mutable target letter pool for tracking consumed letters
        const mutableTargetLetterPoolArray = normalizedUpperCaseTargetWord.split('');
        const splitGuessLettersArray = normalizedUpperCaseGuessWord.split('');
        // ---- PASS ONE: Find exact matches (green) ----
        const indicesMarkedAsExactMatchInPassOne = [];
        for (let passOneIterationIndex = 0; passOneIterationIndex < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS; passOneIterationIndex++) {
            const guessLetterAtThisPosition = splitGuessLettersArray[passOneIterationIndex];
            const targetLetterAtThisPosition = mutableTargetLetterPoolArray[passOneIterationIndex];
            if (guessLetterAtThisPosition === targetLetterAtThisPosition) {
                finalEvaluationResultArray[passOneIterationIndex] = TileStateEnum.CORRECT;
                rawEvaluationCodeResultArray[passOneIterationIndex] = EvaluationResultCode.LETTER_IN_CORRECT_POSITION;
                mutableTargetLetterPoolArray[passOneIterationIndex] = null; // consume it
                indicesMarkedAsExactMatchInPassOne.push(passOneIterationIndex);
            }
        }
        // ---- PASS TWO: Find present letters (yellow) ----
        for (let passTwoOuterIterationIndex = 0; passTwoOuterIterationIndex < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS; passTwoOuterIterationIndex++) {
            // Skip positions already handled in pass one
            if (indicesMarkedAsExactMatchInPassOne.includes(passTwoOuterIterationIndex)) {
                continue;
            }
            const currentGuessLetterBeingChecked = splitGuessLettersArray[passTwoOuterIterationIndex];
            let wasLetterFoundInRemainingTargetPool = false;
            // Search the remaining pool for this letter
            for (let poolSearchInnerIterationIndex = 0; poolSearchInnerIterationIndex < mutableTargetLetterPoolArray.length; poolSearchInnerIterationIndex++) {
                if (mutableTargetLetterPoolArray[poolSearchInnerIterationIndex] === currentGuessLetterBeingChecked) {
                    finalEvaluationResultArray[passTwoOuterIterationIndex] = TileStateEnum.PRESENT;
                    rawEvaluationCodeResultArray[passTwoOuterIterationIndex] = EvaluationResultCode.LETTER_IN_WRONG_POSITION;
                    mutableTargetLetterPoolArray[poolSearchInnerIterationIndex] = null; // consume
                    wasLetterFoundInRemainingTargetPool = true;
                    break; // stop looking
                }
            }
            if (!wasLetterFoundInRemainingTargetPool) {
                // Set to absent (it already is, but let's be explicit)
                finalEvaluationResultArray[passTwoOuterIterationIndex] = TileStateEnum.ABSENT;
                rawEvaluationCodeResultArray[passTwoOuterIterationIndex] = EvaluationResultCode.LETTER_NOT_IN_WORD_AT_ALL;
            }
        }
        const performanceTimerEnd = performance.now();
        const isThisGuessCorrect = finalEvaluationResultArray.every((result) => result === TileStateEnum.CORRECT);
        const theEvaluationResultObject = {
            evaluations: finalEvaluationResultArray,
            rawResultCodes: rawEvaluationCodeResultArray,
            isCorrectGuess: isThisGuessCorrect,
            guessWord: normalizedUpperCaseGuessWord,
            targetWord: normalizedUpperCaseTargetWord,
            processingTimeInMs: performanceTimerEnd - performanceTimerStart
        };
        this._internalEvaluationResultCacheMap.set(evaluationCacheKeyString, theEvaluationResultObject);
        this._totalNumberOfEvaluationsPerformedCounter++;
        return theEvaluationResultObject;
    }
    getTotalNumberOfEvaluationsEverPerformed() {
        return this._totalNumberOfEvaluationsPerformedCounter;
    }
}
// ============================================================
// SECTION 7: TARGET WORD SELECTION MANAGER
// ============================================================
class TargetWordSelectionAndValidationManagerClass {
    constructor(wordListInput) {
        this._theCompleteWordPoolArray = wordListInput.map((w) => w.toUpperCase());
        this._wordLookupSet = new Set(this._theCompleteWordPoolArray);
        this._wordLookupMap = new Map();
        this._wordsAlreadyUsedThisSessionArray = [];
        this._theCurrentlySelectedTargetWord = '';
        // Populate the map (redundant with set)
        this._theCompleteWordPoolArray.forEach((wordEntry) => {
            this._wordLookupMap.set(wordEntry, true);
        });
    }
    selectANewRandomTargetWordAndReturnIt() {
        const unusedWordsFilteredSubset = this._theCompleteWordPoolArray.filter((candidateWord) => !this._wordsAlreadyUsedThisSessionArray.includes(candidateWord));
        const wordPoolToDrawFrom = unusedWordsFilteredSubset.length > 0
            ? unusedWordsFilteredSubset
            : this._theCompleteWordPoolArray;
        const selectedWord = selectOneRandomElementFromTheProvidedArray(wordPoolToDrawFrom);
        this._theCurrentlySelectedTargetWord = selectedWord;
        this._wordsAlreadyUsedThisSessionArray.push(selectedWord);
        return selectedWord;
    }
    getCurrentlySelectedTargetWord() {
        return this._theCurrentlySelectedTargetWord;
    }
    checkWhetherTheProvidedWordIsInTheValidWordList(wordToCheck) {
        const uppercasedWordForComparison = wordToCheck.toUpperCase();
        // Triple check. Two of these are redundant.
        const isInSet = this._wordLookupSet.has(uppercasedWordForComparison);
        const isInMap = this._wordLookupMap.has(uppercasedWordForComparison);
        const isInArray = this._theCompleteWordPoolArray.includes(uppercasedWordForComparison);
        return isInSet && isInMap && isInArray;
    }
}
// ============================================================
// SECTION 8: GAME STATE MANAGER (SINGLETON PATTERN)
// ============================================================
class GameStateManagerSingletonPatternImplementation {
    constructor(initialTargetWord) {
        this._allRegisteredStateChangeListenerCallbacks = [];
        this._snapshotOfPreviousStateBeforeLastMutation = null;
        this._currentGameState = this._buildFreshInitialGameStateObject(initialTargetWord);
    }
    static getExistingInstanceOrCreateNewOne(targetWordForNewInstance) {
        if (!GameStateManagerSingletonPatternImplementation._theOneAndOnlyGlobalInstance) {
            if (!targetWordForNewInstance) {
                throw new Error('GameStateManagerSingletonPatternImplementation requires a targetWord argument for first-time instantiation');
            }
            GameStateManagerSingletonPatternImplementation._theOneAndOnlyGlobalInstance =
                new GameStateManagerSingletonPatternImplementation(targetWordForNewInstance);
        }
        return GameStateManagerSingletonPatternImplementation._theOneAndOnlyGlobalInstance;
    }
    static nukeAndDestroyTheSingletonInstanceForGameReset() {
        GameStateManagerSingletonPatternImplementation._theOneAndOnlyGlobalInstance = null;
    }
    _buildFreshInitialGameStateObject(targetWord) {
        const emptyTileGrid = [];
        for (let rowIterator = 0; rowIterator < GAME_CONFIG_MAXIMUM_NUMBER_OF_ATTEMPTS_ALLOWED_PER_GAME_SESSION; rowIterator++) {
            emptyTileGrid[rowIterator] = [];
            for (let colIterator = 0; colIterator < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS; colIterator++) {
                emptyTileGrid[rowIterator][colIterator] = {
                    letter: '',
                    state: TileStateEnum.EMPTY,
                    row: rowIterator,
                    col: colIterator,
                    evaluated: false,
                    domElement: null,
                    animationInProgress: false,
                    lastUpdatedTimestamp: Date.now(),
                    internalUniqueId: generateCompletelyUnnecessaryPseudoRandomUUIDv4String()
                };
            }
        }
        return {
            targetWord: targetWord.toUpperCase(),
            currentGuess: '',
            guessHistory: [],
            evaluationHistory: [],
            currentRowIndex: 0,
            currentColIndex: 0,
            gameStatus: 'playing',
            hardMode: false,
            lastGuessTimestamp: 0,
            tileDataGrid: emptyTileGrid,
            keyboardLetterEvaluationMap: {},
            totalKeyPressCount: 0,
            totalBackspaceCount: 0
        };
    }
    // Returns deep clone to prevent external mutation (performance: bad)
    getImmutableCopyOfCurrentState() {
        if (STATE_DEEP_CLONE_ON_READ_ENABLED_FLAG) {
            return performDeepCloneOperationOnArbitraryObject(this._currentGameState);
        }
        return this._currentGameState;
    }
    // Returns direct reference to internal state (dangerous, but faster)
    getMutableDirectReferenceToInternalState() {
        return this._currentGameState;
    }
    applyMutationToStateViaCallbackFunction(mutatorCallbackFn) {
        this._snapshotOfPreviousStateBeforeLastMutation =
            performDeepCloneOperationOnArbitraryObject(this._currentGameState);
        mutatorCallbackFn(this._currentGameState);
        this._broadcastStateChangeToAllListeners();
    }
    registerStateChangeListenerCallback(listenerCallback) {
        this._allRegisteredStateChangeListenerCallbacks.push(listenerCallback);
        // Returns unsubscribe function that nobody ever calls
        return () => {
            const listenerIndex = this._allRegisteredStateChangeListenerCallbacks.indexOf(listenerCallback);
            if (listenerIndex > -1) {
                this._allRegisteredStateChangeListenerCallbacks.splice(listenerIndex, 1);
            }
        };
    }
    _broadcastStateChangeToAllListeners() {
        const stateSnapshotForListeners = this.getImmutableCopyOfCurrentState();
        this._allRegisteredStateChangeListenerCallbacks.forEach((listener) => {
            listener(stateSnapshotForListeners);
        });
    }
    getPreviousStateSnapshotOrNull() {
        return this._snapshotOfPreviousStateBeforeLastMutation;
    }
}
GameStateManagerSingletonPatternImplementation._theOneAndOnlyGlobalInstance = null;
// ============================================================
// SECTION 9: DOM MANIPULATION CONTROLLER
// ============================================================
class DOMManipulationAndRenderingControllerClass {
    constructor() {
        this._tileElementRegistryLookupMap = new Map();
        this._keyButtonElementRegistryLookupMap = new Map();
        this._mainGameBoardDivElement = null;
        this._keyboardContainerDivElement = null;
        this._toastNotificationContainerDivElement = null;
        this._currentlyActiveToastElementsList = [];
        this._mainGameBoardDivElement = document.getElementById('GAME_BOARD_DIV');
        this._keyboardContainerDivElement = document.getElementById('keyboardContainerDivWrapper');
        this._toastNotificationContainerDivElement = document.getElementById('toast_notification_container_div');
    }
    constructAndInjectBoardRowsAndTilesIntoDom() {
        if (!this._mainGameBoardDivElement) {
            console.error('[DOMController] Cannot build board: game board element not found in DOM');
            return;
        }
        this._mainGameBoardDivElement.innerHTML = '';
        for (let rowConstructionIterator = 0; rowConstructionIterator < GAME_CONFIG_MAXIMUM_NUMBER_OF_ATTEMPTS_ALLOWED_PER_GAME_SESSION; rowConstructionIterator++) {
            const newRowDivElement = document.createElement('div');
            newRowDivElement.className = 'board-row boardRow';
            newRowDivElement.id = `board-row-${rowConstructionIterator}`;
            newRowDivElement.setAttribute('data-row-index', String(rowConstructionIterator));
            newRowDivElement.setAttribute('role', 'group');
            for (let colConstructionIterator = 0; colConstructionIterator < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS; colConstructionIterator++) {
                const newTileDivElement = document.createElement('div');
                const tileRegistryKey = this._computeTileRegistryKeyFromRowAndCol(rowConstructionIterator, colConstructionIterator);
                newTileDivElement.className = 'tile gameTile game-tile';
                newTileDivElement.id = `tile-${rowConstructionIterator}-${colConstructionIterator}`;
                newTileDivElement.setAttribute('data-row', String(rowConstructionIterator));
                newTileDivElement.setAttribute('data-col', String(colConstructionIterator));
                newTileDivElement.setAttribute('data-letter', '');
                newTileDivElement.setAttribute('data-state', TileStateEnum.EMPTY);
                newTileDivElement.setAttribute('data-uuid', generateCompletelyUnnecessaryPseudoRandomUUIDv4String());
                newTileDivElement.setAttribute('aria-label', 'empty');
                newTileDivElement.textContent = '';
                newRowDivElement.appendChild(newTileDivElement);
                this._tileElementRegistryLookupMap.set(tileRegistryKey, newTileDivElement);
            }
            this._mainGameBoardDivElement.appendChild(newRowDivElement);
        }
    }
    constructAndInjectKeyboardRowsAndButtonsIntoDom() {
        if (!this._keyboardContainerDivElement) {
            console.error('[DOMController] Cannot build keyboard: container element not found in DOM');
            return;
        }
        this._keyboardContainerDivElement.innerHTML = '';
        GAME_CONFIG_KEYBOARD_ROWS_KEY_LAYOUT_DEFINITION.forEach((keyRowDefinitionArray, keyRowIndex) => {
            const keyboardRowDivElement = document.createElement('div');
            keyboardRowDivElement.className = 'keyboard-row keyboardRow';
            keyboardRowDivElement.id = `keyboard-row-${keyRowIndex}`;
            keyRowDefinitionArray.forEach((keyValueString) => {
                const isWideSpecialKey = keyValueString === 'ENTER' || keyValueString === '⌫';
                const keyButtonElement = document.createElement('button');
                keyButtonElement.className = isWideSpecialKey
                    ? 'key keyButton keyboard-key wide-key'
                    : 'key keyButton keyboard-key';
                keyButtonElement.setAttribute('data-key', keyValueString);
                keyButtonElement.setAttribute('data-wide', String(isWideSpecialKey));
                keyButtonElement.setAttribute('data-state', 'empty');
                keyButtonElement.setAttribute('type', 'button');
                keyButtonElement.textContent = keyValueString;
                // Key click handler dispatches to global event bus (unnecessary indirection)
                keyButtonElement.addEventListener('click', (clickEvent) => {
                    clickEvent.preventDefault();
                    clickEvent.stopPropagation();
                    window.dispatchEvent(new CustomEvent('wordle:virtualkey', {
                        detail: {
                            keyValue: keyValueString,
                            eventSource: 'on-screen-keyboard-click',
                            timestamp: Date.now()
                        }
                    }));
                });
                keyboardRowDivElement.appendChild(keyButtonElement);
                // Only register non-wide keys (ENTER and ⌫ aren't tracked for coloring)
                if (!isWideSpecialKey) {
                    this._keyButtonElementRegistryLookupMap.set(keyValueString, keyButtonElement);
                }
            });
            this._keyboardContainerDivElement.appendChild(keyboardRowDivElement);
        });
    }
    _computeTileRegistryKeyFromRowAndCol(row, col) {
        // Could just be `${row},${col}` but enterprise naming is important
        return `ROW_${row}__COL_${col}__TILE_KEY`;
    }
    getTileElementByRowAndColOrReturnNull(row, col) {
        const lookupKey = this._computeTileRegistryKeyFromRowAndCol(row, col);
        return this._tileElementRegistryLookupMap.get(lookupKey) || null;
    }
    updateTileVisualDisplayWithNewLetterValue(row, col, letter) {
        const targetTileElement = this.getTileElementByRowAndColOrReturnNull(row, col);
        if (!targetTileElement)
            return;
        targetTileElement.textContent = letter;
        targetTileElement.setAttribute('data-letter', letter);
        targetTileElement.setAttribute('aria-label', letter || 'empty');
        if (letter !== '') {
            targetTileElement.setAttribute('data-state', TileStateEnum.TBD);
            // Remove animation class, force browser to reflow, re-add to restart it
            targetTileElement.classList.remove('pop', 'tile-pop-anim');
            void targetTileElement.offsetWidth; // intentional reflow hack
            targetTileElement.classList.add('pop', 'tile-pop-anim');
            setTimeout(() => {
                targetTileElement.classList.remove('pop', 'tile-pop-anim');
            }, ANIMATION_TILE_POP_DURATION_IN_MILLISECONDS + 50);
        }
        else {
            targetTileElement.setAttribute('data-state', TileStateEnum.EMPTY);
        }
    }
    performStaggeredRevealAnimationForEntireRow(rowIndex, evaluationsForRow, lettersInRow, callbackFunctionToRunWhenAllAnimationsComplete) {
        evaluationsForRow.forEach((tileEvaluation, colIndex) => {
            const staggeredDelayMs = colIndex * TILE_REVEAL_STAGGER_DELAY_MS_PER_COLUMN;
            setTimeout(() => {
                const tileEl = this.getTileElementByRowAndColOrReturnNull(rowIndex, colIndex);
                if (!tileEl)
                    return;
                // Phase 1 of 2: rotate tile to 90deg (hiding it)
                tileEl.style.transition = `transform ${ANIMATION_FLIP_REVEAL_DURATION_IN_MILLISECONDS / 2}ms ease-in`;
                tileEl.style.transform = 'rotateX(-90deg)';
                setTimeout(() => {
                    // Phase 2 of 2: apply color, rotate back to 0deg (showing colored tile)
                    tileEl.setAttribute('data-state', tileEvaluation);
                    tileEl.classList.remove('correct', 'present', 'absent', 'tbd', 'empty');
                    tileEl.classList.add(tileEvaluation);
                    tileEl.style.transition = `transform ${ANIMATION_FLIP_REVEAL_DURATION_IN_MILLISECONDS / 2}ms ease-out`;
                    tileEl.style.transform = 'rotateX(0deg)';
                    // Trigger callback after last tile animates
                    const isThisTheLastColumn = colIndex === GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS - 1;
                    if (isThisTheLastColumn) {
                        setTimeout(() => {
                            callbackFunctionToRunWhenAllAnimationsComplete();
                        }, (ANIMATION_FLIP_REVEAL_DURATION_IN_MILLISECONDS / 2) + 50);
                    }
                }, ANIMATION_FLIP_REVEAL_DURATION_IN_MILLISECONDS / 2);
            }, staggeredDelayMs);
        });
    }
    applyShakeAnimationToRowAtIndex(targetRowIndex) {
        const rowElement = document.getElementById(`board-row-${targetRowIndex}`);
        if (!rowElement)
            return;
        rowElement.classList.remove('shake', 'shaking', 'shaking-row');
        void rowElement.offsetWidth; // force reflow to restart animation
        rowElement.classList.add('shake', 'shaking', 'shaking-row');
        setTimeout(() => {
            rowElement.classList.remove('shake', 'shaking', 'shaking-row');
        }, ANIMATION_SHAKE_INVALID_WORD_DURATION_IN_MILLISECONDS);
    }
    updateKeyboardButtonVisualStateForLetter(letterToUpdate, newEvaluationState) {
        const keyElement = this._keyButtonElementRegistryLookupMap.get(letterToUpdate.toUpperCase());
        if (!keyElement)
            return;
        const currentStateAttribute = keyElement.getAttribute('data-state') || 'empty';
        // Priority map: higher number = higher priority, won't be downgraded
        const evaluationStatePriorityMap = {
            'correct': 3,
            'present': 2,
            'absent': 1,
            'tbd': 0,
            'empty': 0
        };
        const incomingStatePriority = evaluationStatePriorityMap[newEvaluationState] || 0;
        const existingStatePriority = evaluationStatePriorityMap[currentStateAttribute] || 0;
        if (incomingStatePriority > existingStatePriority) {
            keyElement.setAttribute('data-state', newEvaluationState);
            keyElement.classList.remove('correct', 'present', 'absent');
            keyElement.classList.add(newEvaluationState);
        }
    }
    displayToastNotificationMessageToUser(options) {
        if (!this._toastNotificationContainerDivElement)
            return;
        const toastElement = document.createElement('div');
        toastElement.className = 'toast-message toastMessage';
        toastElement.textContent = options.message;
        this._toastNotificationContainerDivElement.appendChild(toastElement);
        this._currentlyActiveToastElementsList.push(toastElement);
        const displayDurationMs = options.duration !== undefined ? options.duration : TOAST_MESSAGE_DEFAULT_DISPLAY_DURATION_MS;
        setTimeout(() => {
            toastElement.classList.add('toast-fade-out', 'fading');
            setTimeout(() => {
                if (toastElement.parentNode) {
                    toastElement.parentNode.removeChild(toastElement);
                }
                const toastIndexInList = this._currentlyActiveToastElementsList.indexOf(toastElement);
                if (toastIndexInList > -1) {
                    this._currentlyActiveToastElementsList.splice(toastIndexInList, 1);
                }
            }, TOAST_MESSAGE_FADE_OUT_DURATION_MS);
        }, displayDurationMs);
    }
    performWinCelebrationBounceAnimationOnRow(winningRowIndex) {
        for (let tileColumnIndex = 0; tileColumnIndex < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS; tileColumnIndex++) {
            const tileElementForBounce = this.getTileElementByRowAndColOrReturnNull(winningRowIndex, tileColumnIndex);
            if (!tileElementForBounce)
                continue;
            const perTileBounceDelayMs = tileColumnIndex * 100;
            setTimeout(() => {
                tileElementForBounce.style.transition = 'transform 0.1s ease-out';
                tileElementForBounce.style.transform = 'translateY(-30px)';
                setTimeout(() => {
                    tileElementForBounce.style.transition = 'transform 0.15s ease-in';
                    tileElementForBounce.style.transform = 'translateY(0px)';
                }, 100);
            }, perTileBounceDelayMs);
        }
    }
}
// ============================================================
// SECTION 10: USER INPUT HANDLER
// ============================================================
class UserInputEventHandlerAndProcessorController {
    constructor(domController, stateManager, wordManager, evaluationEngine) {
        this._inputAcceptanceIsCurrentlyEnabled = true;
        this._guessSubmissionIsCurrentlyBeingProcessed = false;
        this._domRenderingController = domController;
        this._gameStateManagerInstance = stateManager;
        this._wordSelectionAndValidationManager = wordManager;
        this._wordEvaluationEngine = evaluationEngine;
    }
    registerAllGameInputEventListeners() {
        // Physical keyboard input
        document.addEventListener('keydown', (physicalKeyEvent) => {
            this._routePhysicalKeyboardEventToAppropriateHandler(physicalKeyEvent);
        });
        // Virtual on-screen keyboard input (comes through custom window event)
        window.addEventListener('wordle:virtualkey', (windowEvent) => {
            const customKeyEvent = windowEvent;
            this._routeVirtualKeyboardKeyValueToAppropriateHandler(customKeyEvent.detail.keyValue);
        });
    }
    _routePhysicalKeyboardEventToAppropriateHandler(event) {
        if (!this._inputAcceptanceIsCurrentlyEnabled)
            return;
        if (this._guessSubmissionIsCurrentlyBeingProcessed)
            return;
        const rawKeyValue = event.key;
        if (rawKeyValue === 'Enter') {
            this._handleEnterKeyPressAction();
        }
        else if (rawKeyValue === 'Backspace' || rawKeyValue === 'Delete') {
            this._handleBackspaceOrDeleteKeyPressAction();
        }
        else if (rawKeyValue.length === 1 && /^[a-zA-Z]$/.test(rawKeyValue)) {
            this._handleAlphabeticLetterKeyPressAction(rawKeyValue.toUpperCase());
        }
        // Everything else: silently ignored
    }
    _routeVirtualKeyboardKeyValueToAppropriateHandler(keyValue) {
        if (!this._inputAcceptanceIsCurrentlyEnabled)
            return;
        if (this._guessSubmissionIsCurrentlyBeingProcessed)
            return;
        if (keyValue === 'ENTER') {
            this._handleEnterKeyPressAction();
        }
        else if (keyValue === '⌫') {
            this._handleBackspaceOrDeleteKeyPressAction();
        }
        else if (keyValue.length === 1 && /^[a-zA-Z]$/.test(keyValue)) {
            this._handleAlphabeticLetterKeyPressAction(keyValue.toUpperCase());
        }
    }
    _handleAlphabeticLetterKeyPressAction(uppercasedLetterCharacter) {
        const currentLiveState = this._gameStateManagerInstance.getMutableDirectReferenceToInternalState();
        if (currentLiveState.gameStatus !== 'playing')
            return;
        if (currentLiveState.currentGuess.length >= GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS)
            return;
        const columnIndexToFill = currentLiveState.currentGuess.length;
        this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
            mutableState.currentGuess += uppercasedLetterCharacter;
            mutableState.currentColIndex = mutableState.currentGuess.length;
            mutableState.totalKeyPressCount++;
        });
        this._domRenderingController.updateTileVisualDisplayWithNewLetterValue(currentLiveState.currentRowIndex, columnIndexToFill, uppercasedLetterCharacter);
    }
    _handleBackspaceOrDeleteKeyPressAction() {
        const currentLiveState = this._gameStateManagerInstance.getMutableDirectReferenceToInternalState();
        if (currentLiveState.gameStatus !== 'playing')
            return;
        if (currentLiveState.currentGuess.length === 0)
            return;
        const columnIndexToClear = currentLiveState.currentGuess.length - 1;
        this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
            mutableState.currentGuess = mutableState.currentGuess.slice(0, -1);
            mutableState.currentColIndex = mutableState.currentGuess.length;
            mutableState.totalBackspaceCount++;
        });
        this._domRenderingController.updateTileVisualDisplayWithNewLetterValue(currentLiveState.currentRowIndex, columnIndexToClear, '');
    }
    _handleEnterKeyPressAction() {
        const currentLiveState = this._gameStateManagerInstance.getMutableDirectReferenceToInternalState();
        if (currentLiveState.gameStatus !== 'playing')
            return;
        // Validate: enough letters
        if (currentLiveState.currentGuess.length < GAME_CONFIG_WORD_LENGTH_NUMBER_OF_CHARACTERS) {
            this._domRenderingController.displayToastNotificationMessageToUser({
                message: 'Not enough letters',
                type: 'error'
            });
            this._domRenderingController.applyShakeAnimationToRowAtIndex(currentLiveState.currentRowIndex);
            return;
        }
        // Validate: word in list
        const isValidWord = this._wordSelectionAndValidationManager
            .checkWhetherTheProvidedWordIsInTheValidWordList(currentLiveState.currentGuess);
        if (!isValidWord) {
            this._domRenderingController.displayToastNotificationMessageToUser({
                message: 'Not in word list',
                type: 'error'
            });
            this._domRenderingController.applyShakeAnimationToRowAtIndex(currentLiveState.currentRowIndex);
            return;
        }
        // Lock input during animation
        this._guessSubmissionIsCurrentlyBeingProcessed = true;
        this.setWhetherInputIsCurrentlyAccepted(false);
        // Capture values before mutation
        const guessBeingSubmitted = currentLiveState.currentGuess;
        const targetWordForEvaluation = currentLiveState.targetWord;
        const rowIndexBeingSubmitted = currentLiveState.currentRowIndex;
        // Evaluate!
        const evaluationResultObject = this._wordEvaluationEngine.evaluateGuessWordAgainstTargetWord(guessBeingSubmitted, targetWordForEvaluation);
        // Update state (store guess and evaluation, reset current guess)
        this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
            mutableState.guessHistory.push(guessBeingSubmitted);
            mutableState.evaluationHistory.push(evaluationResultObject.evaluations);
            mutableState.currentGuess = '';
            mutableState.currentColIndex = 0;
            mutableState.lastGuessTimestamp = Date.now();
        });
        // Start reveal animation, then handle win/loss/continue
        this._domRenderingController.performStaggeredRevealAnimationForEntireRow(rowIndexBeingSubmitted, evaluationResultObject.evaluations, guessBeingSubmitted.split(''), () => {
            // Update keyboard key colors
            guessBeingSubmitted.split('').forEach((letterChar, letterIndex) => {
                const letterEval = evaluationResultObject.evaluations[letterIndex];
                this._domRenderingController.updateKeyboardButtonVisualStateForLetter(letterChar, letterEval);
                this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
                    const currentLetterEval = mutableState.keyboardLetterEvaluationMap[letterChar];
                    const priorityTable = {
                        correct: 3, present: 2, absent: 1, tbd: 0, empty: 0
                    };
                    const incomingPriority = priorityTable[letterEval] || 0;
                    const existingPriority = priorityTable[currentLetterEval || 'empty'] || 0;
                    if (incomingPriority > existingPriority) {
                        mutableState.keyboardLetterEvaluationMap[letterChar] = letterEval;
                    }
                });
            });
            // Handle win
            if (evaluationResultObject.isCorrectGuess) {
                this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
                    mutableState.gameStatus = 'won';
                });
                this._domRenderingController.performWinCelebrationBounceAnimationOnRow(rowIndexBeingSubmitted);
                const winMessagesByAttemptNumber = ['Genius!', 'Magnificent!', 'Impressive!', 'Splendid!', 'Great!', 'Phew!'];
                const attemptNumberOneBased = rowIndexBeingSubmitted + 1;
                const winMessageToDisplay = winMessagesByAttemptNumber[Math.min(attemptNumberOneBased - 1, 5)];
                setTimeout(() => {
                    this._domRenderingController.displayToastNotificationMessageToUser({
                        message: winMessageToDisplay,
                        duration: 2000,
                        type: 'success'
                    });
                }, 400);
                this._guessSubmissionIsCurrentlyBeingProcessed = false;
                // Input stays disabled (game over)
            }
            else {
                // Handle lose or continue
                const nextRowIndex = rowIndexBeingSubmitted + 1;
                if (nextRowIndex >= GAME_CONFIG_MAXIMUM_NUMBER_OF_ATTEMPTS_ALLOWED_PER_GAME_SESSION) {
                    // LOSE
                    this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
                        mutableState.gameStatus = 'lost';
                        mutableState.currentRowIndex = nextRowIndex;
                    });
                    setTimeout(() => {
                        this._domRenderingController.displayToastNotificationMessageToUser({
                            message: targetWordForEvaluation,
                            duration: 3500,
                            type: 'error'
                        });
                    }, 300);
                    this._guessSubmissionIsCurrentlyBeingProcessed = false;
                    // Input stays disabled (game over)
                }
                else {
                    // CONTINUE: advance to next row
                    this._gameStateManagerInstance.applyMutationToStateViaCallbackFunction((mutableState) => {
                        mutableState.currentRowIndex = nextRowIndex;
                    });
                    this._guessSubmissionIsCurrentlyBeingProcessed = false;
                    this.setWhetherInputIsCurrentlyAccepted(true);
                }
            }
        });
    }
    setWhetherInputIsCurrentlyAccepted(acceptingInput) {
        this._inputAcceptanceIsCurrentlyEnabled = acceptingInput;
    }
}
// ============================================================
// SECTION 11: MAIN GAME ORCHESTRATOR
// (wraps everything in yet another class)
// ============================================================
class MainWordleGameOrchestratorAndCoordinatorClass {
    constructor() {
        this._gameIsFullyInitializedFlag = false;
        // Step 1: Create word manager
        this._wordSelectionAndValidationManagerInstance =
            new TargetWordSelectionAndValidationManagerClass(THE_COMPLETE_AND_EXHAUSTIVE_LIST_OF_ALL_VALID_FIVE_LETTER_WORDS);
        // Step 2: Pick a target word
        const theChosenTargetWord = this._wordSelectionAndValidationManagerInstance.selectANewRandomTargetWordAndReturnIt();
        // Step 3: Destroy any previous singleton (for new game support)
        GameStateManagerSingletonPatternImplementation.nukeAndDestroyTheSingletonInstanceForGameReset();
        // Step 4: Create state manager with the chosen word
        this._gameStateManagerSingletonInstance =
            GameStateManagerSingletonPatternImplementation.getExistingInstanceOrCreateNewOne(theChosenTargetWord);
        // Step 5: Create DOM controller
        this._domManipulationControllerInstance = new DOMManipulationAndRenderingControllerClass();
        // Step 6: Create evaluation engine
        this._wordEvaluationEngineInstance = new WordEvaluationEngineV2TheFinalVersion();
        // Step 7: Create input handler (depends on all of the above)
        this._userInputHandlerInstance = new UserInputEventHandlerAndProcessorController(this._domManipulationControllerInstance, this._gameStateManagerSingletonInstance, this._wordSelectionAndValidationManagerInstance, this._wordEvaluationEngineInstance);
    }
    // All of these methods are synchronous but wrapped in async for "scalability"
    initializeGameAndBeginPlay() {
        return __awaiter(this, void 0, void 0, function* () {
            yield this._stepOneInitializeDomStructures();
            yield this._stepTwoRegisterInputEventListeners();
            yield this._stepThreeSetupStateObserversAndSubscriptions();
            this._gameIsFullyInitializedFlag = true;
            if (DEV_MODE_LOG_TARGET_WORD_TO_CONSOLE) {
                console.log('[Wordle DEV] Target word:', this._wordSelectionAndValidationManagerInstance.getCurrentlySelectedTargetWord());
            }
            console.log('[WordleOrchestrator] Game successfully initialized and ready for play');
        });
    }
    _stepOneInitializeDomStructures() {
        return __awaiter(this, void 0, void 0, function* () {
            this._domManipulationControllerInstance.constructAndInjectBoardRowsAndTilesIntoDom();
            this._domManipulationControllerInstance.constructAndInjectKeyboardRowsAndButtonsIntoDom();
        });
    }
    _stepTwoRegisterInputEventListeners() {
        return __awaiter(this, void 0, void 0, function* () {
            this._userInputHandlerInstance.registerAllGameInputEventListeners();
        });
    }
    _stepThreeSetupStateObserversAndSubscriptions() {
        return __awaiter(this, void 0, void 0, function* () {
            // Register a state change listener that mostly does nothing useful
            this._gameStateManagerSingletonInstance.registerStateChangeListenerCallback((updatedGameState) => {
                if (updatedGameState.gameStatus !== 'playing') {
                    this._userInputHandlerInstance.setWhetherInputIsCurrentlyAccepted(false);
                }
                // Could do more here but won't
            });
        });
    }
    isGameFullyInitialized() {
        return this._gameIsFullyInitializedFlag;
    }
}
// ============================================================
// SECTION 12: GLOBAL VARIABLES AND ENTRY POINT
// (mixing global scope with OOP: a classic)
// ============================================================
// Global reference to the orchestrator. Accessible from anywhere. Probably fine.
let g_theMainGameOrchestratorGlobalInstanceReference = null;
// DOMContentLoaded: the real entry point
document.addEventListener('DOMContentLoaded', () => __awaiter(this, void 0, void 0, function* () {
    console.log('[Wordle] DOMContentLoaded fired. Spinning up game engine...');
    try {
        g_theMainGameOrchestratorGlobalInstanceReference = new MainWordleGameOrchestratorAndCoordinatorClass();
        yield g_theMainGameOrchestratorGlobalInstanceReference.initializeGameAndBeginPlay();
    }
    catch (initializationError) {
        console.error('[Wordle] CRITICAL FAILURE during game initialization:', initializationError);
        const emergencyContainer = document.getElementById('toast_notification_container_div');
        if (emergencyContainer) {
            const errorDiv = document.createElement('div');
            errorDiv.style.cssText = 'background:red;color:white;padding:12px;border-radius:4px;font-weight:bold;';
            errorDiv.textContent = 'Game failed to load. Please refresh the page.';
            emergencyContainer.appendChild(errorDiv);
        }
    }
}));
// window.load as a backup check (redundant with DOMContentLoaded but paranoid)
window.addEventListener('load', () => {
    if (!(g_theMainGameOrchestratorGlobalInstanceReference === null || g_theMainGameOrchestratorGlobalInstanceReference === void 0 ? void 0 : g_theMainGameOrchestratorGlobalInstanceReference.isGameFullyInitialized())) {
        console.warn('[Wordle] window.load: game not initialized yet. This might be fine.');
    }
});
