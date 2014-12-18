/**
 *  TEST: Button Controller
 *
 *  Copyright 2014 Terry Gauchat
 *
 */
definition(
    name: "Button Controller as PIN Input",
    namespace: "CosmicPuppy",
    author: "Terry Gauchat",
    description: "Use a multi-button controller (e.g., Aeon Labs Minimote) as a security PIN code input.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps@2x.png"
)


preferences {
	page(name: "selectButtonPage", title: "Select your button device", nextPage: "setCombinationPage", uninstall: true) {
		section {
			input name: "buttonDevice", type: "capability.button", title: "Button", multiple: false, required: true
		}
		section {
			input name: "pinLength", type: "enum", title: "Desired PIN length 3-9", multiple: false,
            	required: true, options:["1","2","3","4","5","6","7","8","9"], defaultValue: "4";
		}
	}        
	
	page(name: "setCombinationPage")
    page(name: "validatePage")
    page(name: "validActionsPage") 
	
    /*
	page(name: "timeIntervalInputPage", title: "Only during a certain time") {
		section {
			input name: "starting", type: "time", title: "Starting", required: false
			input name: "ending", type: "time", title: "Ending", required: false
		}
	}
    */
} /* preferences */


import groovy.json.JsonSlurper


def setCombinationPage() {
	dynamicPage(name: "setCombinationPage", title: "Set PIN (security code sequence).", nextPage: "validatePage",
    	install: false, uninstall: true) {
        section("PIN Code Buttons in Sequence") {
        	L:{ for( i in  1 .. pinLength.toInteger() ) {
               		input name: "comb_${i}", type: "enum", title: "Sequence $i", mulitple: false, required: true,
                    	options: ["1","2","3","4"];
                }
            }
            href "selectButtonPage", title:"Go Back", description:"Tap to go back"
    	}        
    }    
              
} /* setCombinationPage */


/* 
 * TODO: Can comb be a list variable? (NB: comb[$i] doesn't seem to work.)?
 */
/*
def getCombinationOrder() {
	return {
    	section("PIN Code Buttons in Sequence") {
        	L:{ for( i in  1 .. pinLength.toInteger() ) {
               		input name: "comb_${i}", type: "enum", title: "Sequence $i", mulitple: false, required: true,
                    	options: ["1","2","3","4"];
                }
            }
            href "selectButtonPage", title:"Go Back", description:"Tap to go back"
        }
    }
} */ /* getCombinationOrder */


def validatePage() {
    def valid = true
    def pageProperties = [
        name: "validatePage",
        title: "Validation",
        install: true,
        uninstall: true
    ]    
    
    /*
     * TODO: This should be dynamic length loop, but I need to figure out how to dynamically String substitute,
     *       if that's even possible! Maybe some form of Eval() would work?
     */
    state.pinSeqList = []
    state.pinLength = pinLength.toInteger()
    switch ( state.pinLength ) {
    	case 9:
            state.pinSeqList << comb_9
    	case 8..9:
            state.pinSeqList << comb_8
    	case 7..9:
            state.pinSeqList << comb_7
    	case 6..9:
            state.pinSeqList << comb_6
    	case 5..9:
            state.pinSeqList << comb_5
    	case 4..9:
            state.pinSeqList << comb_4
    	case 3..9:
            state.pinSeqList << comb_3
    	case 2..9:
            state.pinSeqList << comb_2
    	case 1..9:
            state.pinSeqList << comb_1
    }
    state.pinSeqList.reverse(true) // true --> mutate original list instead of a copy.
    log.debug "pinSeqList is $state.pinSeqList"
    log.debug "pinLength is $state.pinLength"
        	
    return dynamicPage(pageProperties) {
        section() {
            paragraph "PIN Code set to: " + "$state.pinSeqList"
            href "setCombinationPage", title:"Go Back", description:"Tap to change PIN Code sequence."
        }
        section("Lights") {
            input "lights", "capability.switch", title: "Pushed", multiple: true, required: false
        }
        section("Locks") {
            input "locks", "capability.lock", title: "Pushed", multiple: true, required: false
        }
        section("Modes") {
            input "modes", "mode", title: "Pushed", required: false
        }
        def phrases = location.helloHome?.getPhrases()*.label
        if (phrases) {
            section("Hello Home Actions") {
                log.trace phrases
                input "phrases", "enum", title: "Pushed", required: false, options: phrases
            }
        }            
    }
} /* validatePage */


def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(buttonDevice, "button", buttonEvent)
    state.inputDigitsList = []
}

def configured() {
	return
}


/*
 * NB: On the Aeon Minimote, pressing the same key twice is "sort of" filtered unless you wait for the red LED confirmation response.
 *     The two presses are probably detectable by analyzing the buttonDevice.events log, but the stream seems inconsistent.
 *     Therefore, for now, the User MUST wait for confirmation after each keypress; though, frankly, this is less critical if there are no double presses.
 */
def buttonEvent(evt){
	def allOK = true;
	if(true) {
		def value = evt.value
        def slurper = new JsonSlurper()
        def dataMap = slurper.parseText(evt.data)
        def buttonNumber = dataMap.buttonNumber
		log.debug "PIN buttonEvent Device: [$buttonDevice.name], Name: [$evt.name], Value: [$evt.value], Data: [$evt.data], ButtonNumber: [$dataMap.buttonNumber]"
        if(value == "pushed") {
        	state.inputDigitsList << buttonNumber.toString()
            if(state.inputDigitsList.size > state.pinLength) {
            	state.inputDigitsList.remove(state.inputDigitsList.size - state.pinLength - 1)
            }    
            log.debug "PIN Current inputDigitsList: [$state.inputDigitsList]"
            if(state.inputDigitsList.equals(state.pinSeqList)) {	
            	log.debug "PIN MATCH FOUND!!! [$state.pinSeqList]"
                executeHandlers()
            } else {
            	log.debug "PIN no match: [$state.pinSeqList]"
            }    
        }    

    /*
     * TODO: If the above code misses button presses that occur too quickly, considering scanning back through the event log.
     * The behavior if this is a little confusing though: Repeated keys will be seen.
     * Could we limit data entry to 10 or 20 seconds and limit the backscan to the length of the PIN?
     * The only time multiple event backscan seems to apply is for multi-presses of the same key. But then this is essential.
     * Yet eventsSince seems to only be reporting NEW events.
     */
	//	def recentEvents = buttonDevice.eventsSince(new Date(now() - 10000),
    //    	[max:pinLength.toInteger()]).findAll{it.value == evt.value && it.data == evt.data}
	//	log.debug "PIN Found ${recentEvents.size()?:0} events in past 10 seconds"
    //  recentEvents.eachWithIndex { it, i -> log.debug "PIN [$i] Value:$it.value Data:$it.data" }
	}
	return
}
	

// TODO: implement event handlers
def executeHandlers() {
	log.debug "executeHandlers (toggle)"

	def lights = find('lights')
	if (lights != null) toggle(lights)

	def locks = find('locks')
	if (locks != null) toggle(locks)

	def mode = find('mode')
	if (mode != null) changeMode(mode)

	def phrase = find('phrase')
	if (phrase != null) location.helloHome.execute(phrase)
}

def find(type) {
	def preferenceName = type
	def pref = settings[preferenceName]
	if(pref != null) {
		log.debug "Found: $pref for $preferenceName"
	}

	return pref
}

def toggle(devices) {
	log.debug "toggle: $devices = ${devices*.currentValue('switch')}"

	if (devices*.currentValue('switch').contains('on')) {
		devices.off()
	}
	else if (devices*.currentValue('switch').contains('off')) {
		devices.on()
	}
	else if (devices*.currentValue('lock').contains('locked')) {
		devices.unlock()
	}
	else if (devices*.currentValue('lock').contains('unlocked')) {
		devices.lock()
	}
	else {
		devices.on()
	}
}

def changeMode(mode) {
	log.debug "changeMode: $mode, location.mode = $location.mode, location.modes = $location.modes"

	if (location.mode != mode && location.modes?.find { it.name == mode }) {
		setLocationMode(mode)
	}
}
