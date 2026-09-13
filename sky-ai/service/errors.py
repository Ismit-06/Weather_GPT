from fastapi import HTTPException, status

class ServiceException(HTTPException):
    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(status_code=status_code, detail={"code": code, "message": message})
        self.code = code
        self.message = message

class InvalidImageException(ServiceException):
    def __init__(self, message: str = "The supplied image could not be decoded or is corrupted."):
        super().__init__(status.HTTP_400_BAD_REQUEST, "INVALID_IMAGE", message)

class ImageTooLargeException(ServiceException):
    def __init__(self, message: str = "Image file exceeds maximum permitted size."):
        super().__init__(status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, "IMAGE_TOO_LARGE", message)

class UnprocessableImageException(ServiceException):
    def __init__(self, message: str = "Image dimensions or aspect ratio are outside allowed bounds."):
        super().__init__(status.HTTP_422_UNPROCESSABLE_ENTITY, "UNPROCESSABLE_IMAGE", message)

class ModelUnavailableException(ServiceException):
    def __init__(self, message: str = "Sky AI model is not ready or currently unavailable."):
        super().__init__(status.HTTP_503_SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", message)

class InferenceTimeoutException(ServiceException):
    def __init__(self, message: str = "Inference exceeded maximum timeout threshold."):
        super().__init__(status.HTTP_504_GATEWAY_TIMEOUT, "INFERENCE_TIMEOUT", message)

class RateLimitException(ServiceException):
    def __init__(self, message: str = "Request rate limit exceeded. Please throttle requests."):
        super().__init__(status.HTTP_429_TOO_MANY_REQUESTS, "RATE_LIMITED", message)

class ServerOverloadException(ServiceException):
    def __init__(self, message: str = "Maximum concurrent inference requests reached."):
        super().__init__(status.HTTP_503_SERVICE_UNAVAILABLE, "SERVER_OVERLOAD", message)

