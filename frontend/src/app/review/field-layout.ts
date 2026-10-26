import { FormType } from '../api/extraction-api';

/**
 * How each extracted field is shown and edited:
 *  - money: decimal string, shown as dollars
 *  - text: plain text (optional fields may be cleared)
 *  - tin: SSN/EIN, only ever shown masked; editing starts empty (ADR-0005)
 *  - bool: checkbox (W-2 box 13)
 *  - box12: list of code + amount rows
 */
export type FieldKind = 'money' | 'text' | 'tin' | 'bool' | 'box12';

export interface FieldSpec {
  name: string;
  label: string;
  kind: FieldKind;
  /** Can be left empty (sent as null). */
  optional?: boolean;
}

export interface FieldGroup {
  title: string;
  fields: FieldSpec[];
}

/**
 * Field order and labels follow the printed forms, so an advisor can check the screen against the
 * PDF box by box. Names match extraction's models (app/models/w2.py, form_1099nec.py).
 */
export const LAYOUT: Record<FormType, FieldGroup[]> = {
  'W-2': [
    {
      title: 'Employer',
      fields: [
        { name: 'employer_name', label: 'Employer name', kind: 'text' },
        { name: 'employer_ein', label: 'b Employer EIN', kind: 'tin' },
        { name: 'employer_address', label: 'c Employer address', kind: 'text' },
      ],
    },
    {
      title: 'Employee',
      fields: [
        { name: 'employee_name', label: 'e Employee name', kind: 'text', optional: true },
        { name: 'employee_ssn', label: 'a Employee SSN', kind: 'tin', optional: true },
      ],
    },
    {
      title: 'Wages and taxes',
      fields: [
        { name: 'box1_wages', label: '1 Wages, tips, other compensation', kind: 'money' },
        {
          name: 'box2_federal_withholding',
          label: '2 Federal income tax withheld',
          kind: 'money',
        },
        { name: 'box3_ss_wages', label: '3 Social Security wages', kind: 'money' },
        { name: 'box4_ss_tax', label: '4 Social Security tax withheld', kind: 'money' },
        { name: 'box5_medicare_wages', label: '5 Medicare wages and tips', kind: 'money' },
        { name: 'box6_medicare_tax', label: '6 Medicare tax withheld', kind: 'money' },
      ],
    },
    {
      title: 'Boxes 12 and 13',
      fields: [
        { name: 'box12', label: '12 Codes', kind: 'box12' },
        { name: 'box13_retirement_plan', label: '13 Retirement plan', kind: 'bool' },
      ],
    },
    {
      title: 'State',
      fields: [
        { name: 'box15_state', label: '15 State', kind: 'text', optional: true },
        { name: 'box15_state_id', label: "15 Employer's state ID", kind: 'text', optional: true },
        { name: 'box16_state_wages', label: '16 State wages', kind: 'money' },
        { name: 'box17_state_tax', label: '17 State income tax', kind: 'money' },
      ],
    },
  ],
  '1099-NEC': [
    {
      title: 'Payer',
      fields: [
        { name: 'payer_name', label: 'Payer name', kind: 'text' },
        { name: 'payer_tin', label: "Payer's TIN", kind: 'tin' },
        { name: 'payer_address', label: 'Payer address', kind: 'text' },
      ],
    },
    {
      title: 'Recipient',
      fields: [
        { name: 'recipient_name', label: 'Recipient name', kind: 'text', optional: true },
        { name: 'recipient_tin', label: "Recipient's TIN", kind: 'tin', optional: true },
      ],
    },
    {
      title: 'Amounts',
      fields: [
        { name: 'box1a_nonemployee_comp', label: '1a Nonemployee compensation', kind: 'money' },
        {
          name: 'box4_federal_withholding',
          label: '4 Federal income tax withheld',
          kind: 'money',
        },
      ],
    },
    {
      title: 'State',
      fields: [
        { name: 'box6_state', label: '6 State', kind: 'text', optional: true },
        { name: 'box6_state_id', label: "6 Payer's state no.", kind: 'text', optional: true },
        { name: 'box7_state_income', label: '7 State income', kind: 'money' },
      ],
    },
  ],
};
