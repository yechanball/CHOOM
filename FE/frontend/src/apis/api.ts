import axios from "axios";

const axiosInstance = axios.create({
  baseURL: "https://j8a501.p.ssafy.io/api",
  // baseURL: "http://localhost:8080/api",
  headers: {
    "Content-type": "application/json",
  },
});

export const redirectKakao = () => {
  // 카카오 로그인 주소
  const CLIENT_ID = "113e58b998f18be80dd79db4ef86fee2";
  const REDIRECT_URI = "http://localhost:3000/login/oauth2/kakao";
  const KAKAO_AUTH_URL = `https://kauth.kakao.com/oauth/authorize?client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&response_type=code`;
  window.location.href = KAKAO_AUTH_URL;
};

export const loginKakao = async (code: string) => {
  const res = await axiosInstance.get<any>(`/user/login/kakao?code=${code}`);
  return res.data;
};
