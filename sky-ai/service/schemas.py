from typing import Optional, Literal
from pydantic import BaseModel, Field, model_validator

AllowedSceneType = Literal[
    "outdoor_sky", "fabric", "bedsheet", "blanket", "ceiling",
    "wall", "curtain", "indoor_surface", "window_reflection",
    "screen_or_photo", "building", "vegetation", "ground", "unknown"
]

AllowedCloudCondition = Literal[
    "clear", "partly_cloudy", "mostly_cloudy", "overcast",
    "storm_clouds", "haze", "fog"
]

AllowedObstruction = Literal[
    "none", "trees", "buildings", "window_frame", "poles_wires",
    "overhang", "partial_roof", "other", "unknown"
]

AllowedImageQuality = Literal[
    "good", "blurry", "overexposed", "underexposed", "glare", "low_resolution"
]

class SkyAnalysisResponse(BaseModel):
    request_id: str = Field(..., description="Unique request identifier")
    sky_detected: bool = Field(..., description="True ONLY if genuine natural outdoor sky is directly observed")
    sky_confidence: float = Field(..., ge=0.0, le=1.0, description="Confidence score between 0.0 and 1.0")
    scene_type: str = Field(..., description="Identified scene category")
    cloud_condition: Optional[str] = Field(None, description="Observed cloud condition (null if no sky)")
    cloud_coverage: Optional[float] = Field(None, ge=0.0, le=1.0, description="Cloud coverage between 0.0 and 1.0 (null if no sky)")
    visible_precipitation: bool = Field(False, description="Whether rain/snow is visibly falling")
    horizon_visible: bool = Field(False, description="Whether true natural horizon is visible")
    obstruction: str = Field("none", description="Obstructions in view")
    image_quality: str = Field("good", description="Quality of the image")
    model_version: str = Field("SKY-LORA-002", description="Serving model / adapter version")

    @model_validator(mode="after")
    def validate_semantic_consistency(self):
        # Strict rule: if sky is not detected, cloud details MUST be null
        if not self.sky_detected:
            self.cloud_condition = None
            self.cloud_coverage = None
            self.visible_precipitation = False
            if self.scene_type == "outdoor_sky":
                self.scene_type = "unknown"
        else:
            if self.scene_type != "outdoor_sky":
                self.scene_type = "outdoor_sky"
        return self

class ErrorDetail(BaseModel):
    code: str
    message: str

class ErrorResponse(BaseModel):
    request_id: str
    error: ErrorDetail

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str

class ReadyResponse(BaseModel):
    ready: bool

class FusionRequest(BaseModel):
    context: dict
    question: Optional[str] = None

class FusionResponse(BaseModel):
    status: str
    observed_summary: str
    reported_summary: str
    radar_summary: str
    disagreement: dict
    answer: str
    confidence_level: str

