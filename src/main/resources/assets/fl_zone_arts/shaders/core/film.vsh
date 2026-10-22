#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int ZaFogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec3 normal;
out vec3 toEye;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, ZaFogShape);
    vertexColor = Color;
    normal = Normal;
    toEye = -(IViewRotMat * view.xyz);
}
