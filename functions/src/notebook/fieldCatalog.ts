/**
 * Mirrors the measurement field keys in BodyProfileTemplate.kt. Labels are the EN and FR
 * strings (measurement_field_<key>) plus common notebook shorthand.
 */
export interface CatalogField {
  key: string;
  gender: 'female' | 'male';
  labels: string[];
}

export const FIELD_CATALOG: CatalogField[] = [
  // Female
  { key: 'shoulder_width', gender: 'female', labels: ['Shoulder', 'Épaule', 'Sh', 'Shd'] },
  { key: 'bust_circumference', gender: 'female', labels: ['Bust', 'Tour de poitrine', 'Rd'] },
  { key: 'bust_point', gender: 'female', labels: ['Shoulder to nipple point', 'Hauteur de poitrine'] },
  { key: 'shoulder_to_underbust', gender: 'female', labels: ['Shoulder to underbust', 'Hauteur sous-poitrine'] },
  { key: 'bust_span', gender: 'female', labels: ['Bust span', 'Écart de poitrine'] },
  { key: 'waist', gender: 'female', labels: ['Waist', 'Tour de taille', 'Wst', 'W'] },
  { key: 'neck_circumference', gender: 'female', labels: ['Neck circumference', 'Neck', 'Tour de cou', 'Rd'] },
  { key: 'underbust_circumference', gender: 'female', labels: ['Underbust', 'Tour sous-poitrine', 'Rd'] },
  { key: 'shoulder_to_waist', gender: 'female', labels: ['Shoulder to waist', 'Épaule à la taille'] },
  { key: 'hip_circumference', gender: 'female', labels: ['Hip', 'Tour de hanches', 'Hp', 'Rd'] },
  { key: 'full_length_gown', gender: 'female', labels: ['Full length of gown', 'Longueur de robe'] },
  { key: 'sleeve_length', gender: 'female', labels: ['Sleeve length', 'Longueur de manche', 'S/S'] },
  { key: 'wrist_circumference', gender: 'female', labels: ['Wrist', 'Tour de poignet', 'Rd'] },
  { key: 'nape_to_waist', gender: 'female', labels: ['Nape to waist', 'Nuque à la taille'] },
  { key: 'full_front_length', gender: 'female', labels: ['Full front length', 'Longueur devant'] },
  { key: 'arm_length', gender: 'female', labels: ['Arm length', 'Longueur de bras'] },
  { key: 'trouser_waist', gender: 'female', labels: ['Waist', 'Tour de taille', 'Wst', 'W'] },
  { key: 'trouser_length', gender: 'female', labels: ['Length', 'Longueur de pantalon', 'Tr L'] },
  { key: 'trouser_hip', gender: 'female', labels: ['Hip', 'Tour de hanches', 'Hp'] },
  { key: 'thigh_circumference', gender: 'female', labels: ['Thigh', 'Tour de cuisse', 'Rd'] },
  { key: 'inseam', gender: 'female', labels: ['Inseam', 'Entrejambe'] },
  // Male
  { key: 'shoulder_back', gender: 'male', labels: ['Back', 'Carrure dos', 'Sh', 'Shd'] },
  { key: 'chest', gender: 'male', labels: ['Chest', 'Tour de poitrine', 'Ch'] },
  { key: 'shirt_length', gender: 'male', labels: ['Length of kaftan', 'Longueur du caftan'] },
  { key: 'long_sleeve_length', gender: 'male', labels: ['Sleeve length', 'Longueur de manche', 'L/S'] },
  { key: 'elbow_circumference', gender: 'male', labels: ['Elbow circumference', 'Tour de coude', 'Rd'] },
  { key: 'neck_circumference', gender: 'male', labels: ['Neck', 'Tour de cou', 'Rd'] },
  { key: 'tummy', gender: 'male', labels: ['Tummy', 'Tour de ventre', 'Rd'] },
  { key: 'trouser_waist', gender: 'male', labels: ['Waist', 'Tour de taille', 'Wst', 'W'] },
  { key: 'trouser_hip', gender: 'male', labels: ['Hip', 'Tour de hanches', 'Hp'] },
  { key: 'trouser_length', gender: 'male', labels: ['Length', 'Longueur de pantalon', 'Tr L'] },
  { key: 'thigh_circumference', gender: 'male', labels: ['Thigh', 'Tour de cuisse', 'Rd'] },
  { key: 'knee_circumference', gender: 'male', labels: ['Knee', 'Tour de genou', 'Rd'] },
];
