import styled from 'styled-components';
import {themed, themeStyle} from 'modules/theme';

export const FoldButton = themed(styled.div`
  display: flex;
  justify-content: center;
  padding: 9px 10px;
  border-style: ${({type}) =>
    type === 'left' ? 'none none none solid' : 'none solid none none'};
  border-width: 1px;
  border-color: rgba(
    ${themeStyle({dark: '255, 255, 255, 0.15', light: '28, 31, 35, 0.15'})}
  );
`);
