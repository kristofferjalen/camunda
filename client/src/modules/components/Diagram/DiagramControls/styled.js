import styled from 'styled-components';

import Button from 'modules/components/Button';

export const DiagramControls = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: ${({isShifted}) => (isShifted ? '427px' : '5px')};
  bottom: 47px;
  z-index: 2;
  width: 28px;

  transition: right 0.2s ease-out;
`;

export const Box = styled(Button)`
  width: 100%;
  padding: 5px;
  height: 28px;
`;

export const ZoomReset = styled(Box)`
  border-radius: 3px;
  margin-bottom: 10px;
`;

export const ZoomIn = styled(Box)`
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  border-bottom: none;
`;

export const ZoomOut = styled(Box)`
  border-top-left-radius: 0;
  border-top-right-radius: 0;
`;
