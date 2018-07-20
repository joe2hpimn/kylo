import * as angular from 'angular';
import * as _ from "underscore";
import {Component, OnInit, Input, Output, EventEmitter, SimpleChanges} from "@angular/core";
import {PolicyInputFormService} from "./policy-input-form.service";
import {FlexLayoutModule} from "@angular/flex-layout";
import {AbstractControl,FormControl, FormGroup, Validators} from '@angular/forms';
import {MatDatepickerModule} from '@angular/material/datepicker'
import {ValidatorFn} from "@angular/forms/src/directives/validators";
import {FieldConfig} from "../dynamic-form/model/FieldConfig";
import {Textarea} from "../dynamic-form/model/Textarea";
import {Templates} from "../../services/TemplateTypes";
import {SectionHeader} from "../dynamic-form/model/SectionHeader";
import {RadioButton} from "../dynamic-form/model/RadioButton";
import {Checkbox} from "../dynamic-form/model/Checkbox";
import {Select} from "../dynamic-form/model/Select";
import {InputText} from "../dynamic-form/model/InputText";
import {DynamicFormService} from "../dynamic-form/services/dynamic-form.service";
//requires CovalentChipsModule


export function MultipleEmail(control: FormControl) {

        var EMAIL_REGEXP = /^(?=.{1,254}$)(?=.{1,64}@)[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+(\.[-!#$%&'*+/0-9=?A-Z^_`a-z{|}~]+)*@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;
        let emails :string = control.value;
        let invalidEmail  = emails.split(',').find((email: string) => !EMAIL_REGEXP.test(email.trim()));
        let isValid = invalidEmail == undefined;
        return isValid ? null : { 'multipleEmails': 'invalid email' }

    }


    export interface RuleGroupWithFieldConfig {
    fields:FieldConfig<any>[];
    group:any;
    }

@Component({
    selector: "policy-input-form",
    templateUrl: "js/feed-mgr/shared/field-policies-angular2/policy-input-form.component.html"
})
export class PolicyInputFormComponent implements OnInit {

    @Input()
    rule:any;

    @Input()
    parentFormGroup:FormGroup;

    formGroup:FormGroup;

    @Input()
    feed?: string
    @Input()
    mode: string //NEW or EDIT

    @Input()
    onPropertyChange :any

    editChips:any;

    formControls:any = {};

    fieldConfigList:FieldConfig<any>[] = [];

    fieldConfigGroup:RuleGroupWithFieldConfig[] = []


    constructor(private policyInputFormService:PolicyInputFormService, private dynamicFormService:DynamicFormService) {
        this.editChips = {};
        this.editChips.selectedItem = null;
        this.editChips.searchText = null;

        if(this.formGroup == undefined) {
            console.log("Creating new form group!");
            this.formGroup = new FormGroup({});
        }

    }
    ngOnInit() {

        console.log("Attach formGroup to parent", this.formGroup, this.parentFormGroup)
        this.parentFormGroup.registerControl("policyForm",this.formGroup);

        //call the onChange if the form initially sets the value
      /*
        if(this.onPropertyChange != undefined && angular.isFunction(this.onPropertyChange)) {
            _.each(this.rule.properties,  (property:any) => {
                if ((property.type == 'select' || property.type =='feedSelect' || property.type == 'currentFeed') && property.value != null) {
                    this.onPropertyChange()(property);
                }
            });
        }
        */

        if(this.rule) {
            this.createFormFieldConfig();
        }
        else {
            this.rule = {name:''}
        }
        //_.each(this.rule.properties,(property) => this.createFormControls(property));
    }

    ngOnChanges(changes: SimpleChanges) {
        if(changes && changes.rule){
            setTimeout(() => {
            //clear old controls
            Object.keys(this.formGroup.controls).forEach(controlName => this.formGroup.removeControl(controlName));
            console.log("CHANGED RULE ",changes.rule)
            this.createFormFieldConfig();
            })
        }
    }


    private createFormControls2(property:any) {
        let validatorOpts :any[] = [];
        let formControlConfig = {}
        if(property.patternRegExp){
            validatorOpts.push(Validators.pattern(property.patternRegExp))
        }
        if(property.required){
            validatorOpts.push(Validators.required)
        }
        if(property.type == "emails"){
            validatorOpts.push(MultipleEmail)
        }
        if(property.type == "email"){
            validatorOpts.push(Validators.email)
        }
        if(!this.rule.editable){
            formControlConfig = {value:property.value,disabled:true}
        }
        let fc = new FormControl(formControlConfig,validatorOpts);
        this.formGroup.addControl(property.formKey,fc)
    }

    private createFormFieldConfig()
    {
        let order = 0;
        this.fieldConfigGroup = [];
        this.rule.groups.forEach((group:any, idx:number) => {
            if(group.properties){
                let fieldConfigList :FieldConfig<any>[] = [];
                group.properties.forEach((property:any) => {
                    if(property.hidden == false) {
                        let fieldConfig = this.toFieldConfig(property, order);
                        fieldConfigList.push(fieldConfig);
                        order++;
                    }
                });
                this.fieldConfigGroup[idx] = {fields:fieldConfigList, group:group};
            }
        });
        //this.dynamicFormService.addToFormGroup(this.fieldConfigList, this.formGroup);
    }



    private isInputText(property:any){
        return (property.type == null || property.type == "string" || property.type == "text" || property.type == "email" || property.type == "number" || property.type == "password" || property.type == 'regex' ||property.type == 'email' || property.type == 'emails' );
    }

    private isSelect(property:any){
       return  property.type == 'select' || property.type == 'feedSelect' || property.type == 'currentFeed' || property.type =='velocityTemplate';
    }


    private  toFieldConfigOptions(property :any):any {
        let key:string = property.formKey;
        let options = {key:key,label:property.displayName,required:property.required,placeholder:property.placeholder, value:property.value,hint:property.hint,pattern:property.patternRegExp};
        return options;

    }

    private  toFieldConfig(property:any, order:number){

            let fieldConfig:FieldConfig<any> = null;
            let fieldConfigOptions :any = this.toFieldConfigOptions(property);
            fieldConfigOptions.order= order;

            if(this.isInputText(property)){
                if(property.type == "string"){
                    fieldConfigOptions.type = "text";
                }
                else if(property.type == "regex"){
                    fieldConfigOptions.type = "text";
                }
                else if(property.type =="emails"){
                    fieldConfigOptions.type = "email";
                }
                else {
                    fieldConfigOptions.type = "text";
                }
                fieldConfig = new InputText(fieldConfigOptions);
            }
            else if(this.isSelect(property)){

                let options :any[] = [];
                if(property.selectableValues.length >0) {
                    //already in label,value objects
                    options = property.selectableValues;
                }
                fieldConfigOptions.options = options;
                fieldConfig = new Select(fieldConfigOptions);
            }
            return fieldConfig;
    }


    queryChipSearch = this.policyInputFormService.queryChipSearch;
    transformChip = this.policyInputFormService.transformChip;


    validateRequiredChips(property:any) {
        return this.policyInputFormService.validateRequiredChips(this.formGroup, property);
    }

    onPropertyChanged(property:any){
        if(this.onPropertyChange != undefined && angular.isFunction(this.onPropertyChange)){
            this.onPropertyChange()(property);
        }
    }

}