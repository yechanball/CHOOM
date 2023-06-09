import React from "react";
import { LoginBtn, Icon, NomalBtn } from "./style";

export type BtnProps = {
  login?: boolean;
  loginType?: "kakao" | "google" | "tiktok" | "youtube_shorts";
  btnText?: string;
  handleClick: () => void;
  width?: string;
  padding?: string;
  margin?: string;
  className?: string;
};

function Btn({
  login,
  loginType,
  btnText,
  handleClick,
  width,
  padding,
  margin,
  className,
}: BtnProps) {
  return login ? (
    <LoginBtn loginType={loginType} onClick={handleClick}>
      <Icon src={`/assets/icon_${loginType}.png`} alt="이미지" height="45px" />
    </LoginBtn>
  ) : (
    <NomalBtn
      className={className}
      width={width}
      padding={padding}
      margin={margin}
      onClick={handleClick}
    >
      {btnText}
    </NomalBtn>
  );
}

export default Btn;
