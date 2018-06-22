import React from 'react';
import {shallow} from 'enzyme';

import Checkbox from './Checkbox';

import * as Styled from './styled';

describe('<Checkbox />', () => {
  const mockOnChange = jest.fn();

  it('should update "isChecked" state according to props', () => {
    // given
    let isChecked = true;
    const node = shallow(
      <Checkbox onChange={mockOnChange} isChecked={isChecked} />
    );
    expect(node.state().isChecked).toBe(true);

    // when
    isChecked = false;
    node.setProps({isChecked});

    // then
    expect(node.state().isChecked).toBe(false);
  });

  it('should toggle "isChecked" state on click', () => {
    const node = shallow(<Checkbox onChange={mockOnChange} isChecked={true} />);
    expect(node.state().isChecked).toBe(true);
    node.find(Styled.Checkbox).simulate('click');
    expect(node.state().isChecked).toBe(false);
  });

  it('should display a label if passed as props', () => {
    const node = shallow(
      <Checkbox label={'foo'} onChange={mockOnChange} isChecked={true} />
    );

    expect(node.find(Styled.Label)).toExist();
  });
});
