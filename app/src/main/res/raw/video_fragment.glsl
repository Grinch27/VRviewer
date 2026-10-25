#extension GL_OES_EGL_image_external : require
// Requirements: zoom and rotate the image in screen space; preserve source-eye isolation.
// Open decisions: lens distortion and HDR tone mapping require separate work.
// Risks/verification: inverse image rotation, finite ray slopes and crop before SurfaceTexture transform.
precision highp float;
uniform samplerExternalOES uTexture;
uniform mat4 uTextureTransform;
uniform mat4 uCameraRotation;
uniform vec4 uEyeRect;
uniform vec2 uTexel;
uniform float uViewportAspect;
uniform float uVideoAspect;
uniform vec2 uTanHalfFov;
uniform float uZoom;
uniform vec2 uRotation;
uniform vec2 uPan;
uniform int uProjection;
varying vec2 vPosition;
const float PI = 3.141592653589793;
vec2 rotateImage(vec2 p) {
    return vec2(uRotation.x * p.x - uRotation.y * p.y, uRotation.y * p.x + uRotation.x * p.y);
}
void main() {
    vec2 uv;
    if (uProjection == 0) {
        vec2 fit = vec2(min(1.0, uVideoAspect / uViewportAspect), min(1.0, uViewportAspect / uVideoAspect));
        vec2 p = rotateImage(vPosition * vec2(uViewportAspect, 1.0) - uPan) / uZoom / (fit * vec2(uViewportAspect, 1.0));
        if (abs(p.x) > 1.0 || abs(p.y) > 1.0) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        uv = vec2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5);
    } else {
        vec2 p = rotateImage(vPosition * vec2(uViewportAspect, 1.0));
        vec3 ray = normalize((uCameraRotation * vec4(p.x / uViewportAspect * uTanHalfFov.x, p.y * uTanHalfFov.y, -1.0, 0.0)).xyz);
        float longitude = atan(ray.x, -ray.z);
        float latitude = asin(clamp(ray.y, -1.0, 1.0));
        if (uProjection == 1 && abs(longitude) > PI * 0.5) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        float span = uProjection == 1 ? PI : 2.0 * PI;
        uv = vec2(longitude / span + 0.5, 0.5 - latitude / PI);
    }
    vec2 cropped = clamp(uEyeRect.xy + uv * uEyeRect.zw, uEyeRect.xy + uTexel * 0.5, uEyeRect.xy + uEyeRect.zw - uTexel * 0.5);
    vec2 textureUv = (uTextureTransform * vec4(cropped.x, 1.0 - cropped.y, 0.0, 1.0)).xy;
    gl_FragColor = texture2D(uTexture, textureUv);
}
