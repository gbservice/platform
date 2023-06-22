// todo --- backward compatibility. option() should be removed in future releases ---
function option() {
    return checkButton();
}

function check() {
    return _defaultRadioCheckBox('checkbox');
}

function radio() {
    return _defaultRadioCheckBox('radio', true);
}

function checkButton() {
    return _checkBoxRadioButtonToggle('checkbox');
}

function radioButton() {
    return _checkBoxRadioButtonToggle('radio', true);
}

function checkButtonGroup() {
    return _checkBoxRadioButtonGroup('checkbox');
}

function radioButtonGroup() {
    return _checkBoxRadioButtonGroup('radio', true);
}

function _defaultRadioCheckBox(type, hasName) {
    return _option(type, false, ['form-check'], ['form-check-input'], ['form-check-label', 'option-item'], hasName);
}

function _checkBoxRadioButtonToggle(type, hasName) {
    return _option(type, false, null, ['btn-check'], ['btn', 'btn-outline-secondary', 'option-item'], hasName);
}

function _checkBoxRadioButtonGroup(type, hasName) {
    return _option(type, true, ['btn-group'], ['btn-check'], ['btn', 'btn-outline-secondary', 'option-item'], hasName);
}

function _option(type, isGroup, divClasses, inputClasses, labelClasses, hasName) {
    let isButton = isGroup || divClasses == null;

    function _getRandomId() {
        return Math.random().toString(36).slice(2);
    }

    /* if isInnerElement==true this is <label> or <input> element.
    * if isInnerElement==false this is <div> element*/
    function _getOptionElement(options, index, isInput, isInnerElement) {
        if (isButton) {
            return options.children[(index * 2) + (isInput ? 0 : 1)]
        } else {
            let option = options.children[index];
            return isInnerElement ? isInput ? option.firstChild : option.lastChild : option;
        }
    }

    return {
        render: function (element) {
            element.name = hasName ? _getRandomId() : null; // radiobutton must have a name attribute

            let options = document.createElement('div');
            options.classList.add(isButton ? "option-btn-container" : "option-container");
            if (isGroup) {
                options.setAttribute("role", "group");
                if (divClasses != null)
                    divClasses.forEach(divClass => options.classList.add(divClass));
            }

            element.appendChild(options);
            element.options = options;
        }, update: function (element, controller, list) {
            let isList = controller.isList();

            if(!isList) {
                if (typeof list === 'string') {
                    let strings = list.split(",");
                    list = [];
                    for (let i = 0; i < strings.length; i++) {
                        list.push({name: strings[i], selected: false});
                    }
                } else if (list == null) {
                    list = [];
                }
            }

            let changed = { dropAndNotSetChecked : false}
            let options = element.options;
            controller.diff(list, element, (changeType, index, object) => {
                switch(changeType) {
                    case 'remove': // clear
                        if (isButton) {
                            options.removeChild(_getOptionElement(options, index, false, false));
                            options.removeChild(_getOptionElement(options, index, true, false));
                        } else {
                            options.removeChild(_getOptionElement(options, index, false, false));
                        }
                        break;
                    case 'add': // render and update
                    case 'update': // update
                        let input, label;
                        if(changeType === 'add') {
                            input = document.createElement('input');
                            inputClasses.forEach(inputClass => input.classList.add(inputClass));
                            input.type = type;
                            input.id = _getRandomId();
                            input.setAttribute("autocomplete", 'off');

                            input.setAttribute('name', element.name);

                            label = document.createElement('label');
                            labelClasses.forEach(labelClass => label.classList.add(labelClass));
                            label.setAttribute('for', input.id);

                            input.addEventListener('change', function () {
                                controller.changeProperty('selected', this.key, this.checked ? true : null);
                                if (isList)
                                    controller.changeObject(this.key);
                            });

                            let currentOptions = options.children;
                            let append = index === (isButton ? currentOptions.length / 2 : currentOptions.length);
                            if (isButton) {
                                if (append) {
                                    options.appendChild(input);
                                    options.appendChild(label);
                                } else {
                                    options.insertBefore(input, currentOptions[index * 2]);
                                    options.insertBefore(label, currentOptions[(index * 2) + 1]);
                                }
                            } else {
                                let div = document.createElement('div');
                                divClasses.forEach(divClass => div.classList.add(divClass));
                                div.appendChild(input);
                                div.appendChild(label);

                                if (append)
                                    options.appendChild(div);
                                else
                                    options.insertBefore(div, currentOptions[index]);
                            }
                        } else {
                            input = _getOptionElement(options, index, true, true);
                            label = _getOptionElement(options, index, false, true);
                        }

                        input.key = object;
                        label.innerText = object.name;

                        let checked = object.selected != null && object.selected;
                        if(checked)
                            changed.dropAndNotSetChecked = false;
                        else
                            if(input.checked)
                                changed.dropAndNotSetChecked = true;
                        input.checked = checked;
                        break;
                }
            });

            // if we dropped (and not set) checked and there are other selected elements - select them
            if(changed.dropAndNotSetChecked) {
                for (let i = 0; i < list.length; i++) {
                    let object = list[i];
                    if(object.selected != null && object.selected) {
                        let input = _getOptionElement(options, i, true, true);
                        input.checked = true;
                        break;
                    }
                }
            }

            for (let i = 0; i < list.length; i++){
                let input = _getOptionElement(options, i, true, true);
                let readonly;
                if (isList) {
                    input.classList[controller.isCurrent(input.key) ? 'add' : 'remove']('option-item-current');
                    readonly = controller.isPropertyReadOnly('selected', input.key);
                } else {
                    readonly = controller.isReadOnly();
                }
                if(readonly)
                    input.setAttribute('onclick', 'return false');
                else
                    input.removeAttribute('onclick')
            }
        }
    }
}