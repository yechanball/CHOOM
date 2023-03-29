import React, {
  useState,
  useEffect,
  useRef,
  forwardRef,
  useImperativeHandle,
} from "react";
import { useParams } from "react-router-dom";
import { getChallengeStatus } from "../../apis/dance";
import { CgEditFlipH } from "react-icons/cg";
import {
  MdSlowMotionVideo,
  MdVolumeUp,
  MdPlayArrow,
  MdVolumeOff,
  MdStop,
} from "react-icons/md";
import CircleBtn from "../CircleBtn/CircleBtn";
import { MainContainer, BtnContainer } from "../Dance/style";
import { ChallengeVideo } from "./style";
import * as poseDetection from "@tensorflow-models/pose-detection";

interface Pose {
  keypoints: poseDetection.Keypoint[];
}

const DanceVideo = forwardRef(
  (
    props: {
      setPoseList: (poseList: Pose[]) => void;
      poseList: Pose[];
      detector: poseDetection.PoseDetector;
      myUrl?: string;
      setTitle: (title: string) => void;
    },
    ref: React.ForwardedRef<any>
  ) => {
    let poseListTemp: Pose[] = [];

    const [videoUrl, setVideoUrl] = useState<string>("");
    const video = useRef<HTMLVideoElement>(null);
    const [playRate, setPlayRate] = useState(1.0);
    const [isFlipped, setIsFlipped] = useState(false);
    const [isMuted, setIsMuted] = useState(video.current?.muted);
    const [isPlaying, setIsPlaying] = useState(false);
    const { danceId } = useParams();
    useImperativeHandle(ref, () => ({
      playVideo,
      changeVideoTime,
    }));

    useEffect(() => {
      getChallengeStatus(danceId!)
        .then((res) => {
          console.log(res);

          // setVideoUrl(URL.createObjectURL(file));
        })
        .catch((err) => console.log(err));
    }, [danceId]);

    // 비디오 업로드
    const uploadVideo = async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = (event.target as HTMLInputElement)?.files?.[0];

      props.setTitle(file?.name.split(".")[0]!);
      if (file !== undefined) {
        setVideoUrl(URL.createObjectURL(file));
      }
    };

    // 분석시작
    const startEstimate = async () => {
      if (video.current) {
        video.current.currentTime = 0;
        video.current.play();

        await runFrame();
      }
    };

    // 분석
    const runFrame = async () => {
      if (video.current?.paused) {
        alert("완료");
        console.log(poseListTemp);
        props.setPoseList(poseListTemp);
        return;
      }

      await renderResult();
      requestAnimationFrame(runFrame);
    };

    // 결과저장
    const renderResult = async () => {
      const estimatePoseList = await props.detector.estimatePoses(
        video.current!
      );

      if (estimatePoseList.length > 0) {
        const newKpts: poseDetection.Keypoint[] = [];
        estimatePoseList[0].keypoints.map((kpt: poseDetection.Keypoint) => {
          newKpts.push({
            x: (kpt.x * 450) / video.current?.videoWidth!,
            y: (kpt.y * 800) / video.current?.videoHeight!,
            z: kpt.z,
            score: kpt.score,
          });
          return newKpts;
        });
        poseListTemp.push({ keypoints: newKpts });
      }
    };

    const playVideo = () => {
      if (video.current) video.current.currentTime = 0;
      video.current?.play();
    };

    const changeVideoTime = (time: number) => {
      console.log(time);
      if (video.current) video.current.currentTime = time;
      video.current?.play();
    };

    // 비디오 재생
    const handelPlayBtnClick = () => {
      setIsPlaying(true);
    };

    const handelStopBtnClick = () => {
      setIsPlaying(false);
    };

    useEffect(() => {
      isPlaying ? video.current?.play() : video.current?.pause();

      const handleEnded = () => {
        setIsPlaying(false);
      };
      if (video.current) video.current.addEventListener("ended", handleEnded);
    }, [isPlaying]);

    // 비디오 속도 변경
    const handelChangeRateBtnClick = () => {
      if (playRate === 1.0) setPlayRate(2.0);
      else if (playRate === 2.0) setPlayRate(0.5);
      else setPlayRate(1.0);
    };

    useEffect(() => {
      if (video.current) video.current.playbackRate = playRate;
    }, [playRate]);

    // 거울모드
    const handelFlipBtnClick = () => {
      setIsFlipped(!isFlipped);
      if (video.current) {
        isFlipped
          ? (video.current.style.transform = "")
          : (video.current.style.transform = "scaleX(-1)");
      }
    };

    // 볼륨조절
    const handelChangeVolumeBtnClick = () => {
      setIsMuted(!isMuted);
    };

    useEffect(() => {
      if (video.current) video.current.muted = isMuted!;
    }, [isMuted]);

    return (
      <div>
        {!props.myUrl ? (
          <MainContainer>
            <ChallengeVideo
              src={videoUrl}
              width={450}
              height={800}
              ref={video}
            />

            <BtnContainer>
              <CircleBtn
                icon={MdSlowMotionVideo}
                onClick={handelChangeRateBtnClick}
                label={"재생 속도"}
                disabled={props.poseList.length === 0 ? "disabled" : ""}
              />
              <CircleBtn
                icon={CgEditFlipH}
                onClick={handelFlipBtnClick}
                label={"좌우 반전"}
                disabled={props.poseList.length === 0 ? "disabled" : ""}
              />
              <CircleBtn
                icon={isMuted ? MdVolumeOff : MdVolumeUp}
                onClick={handelChangeVolumeBtnClick}
                label={"음량"}
                disabled={props.poseList.length === 0 ? "disabled" : ""}
              />

              <CircleBtn
                icon={isPlaying ? MdStop : MdPlayArrow}
                onClick={isPlaying ? handelStopBtnClick : handelPlayBtnClick}
                label={isPlaying ? "정지" : "재생"}
                disabled={props.poseList.length === 0 ? "disabled" : ""}
              />
            </BtnContainer>
          </MainContainer>
        ) : (
          <MainContainer>
            <ChallengeVideo
              src={props.myUrl}
              width={450}
              height={800}
              ref={video}
              controls
            />
          </MainContainer>
        )}
        {!props.myUrl && (
          <div>
            <input
              type="file"
              id="videofile"
              name="video"
              accept="video/*"
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                uploadVideo(e);
              }}
            />
            <button onClick={() => startEstimate()}>시작</button>
          </div>
        )}
      </div>
    );
  }
);

export default DanceVideo;
